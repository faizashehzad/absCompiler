%%This file is licensed under the terms of the Modified BSD License.


-module(cog_monitor).
-behaviour(gen_server).
-include_lib("abs_types.hrl").

-export([start_link/2, stop/0, waitfor/0]).

%% communication about cogs
-export([new_cog/1, cog_active/1, cog_blocked/1, cog_unblocked/1, cog_blocked_for_clock/4, cog_idle/1, cog_died/1]).

%% communication about tasks
-export([task_waiting_for_clock/4]).

%% communication about dcs
-export([new_dc/1, dc_died/1, get_dcs/0]).

%% gen_server interface
-export([init/1, handle_call/3, handle_cast/2, handle_info/2, terminate/2, code_change/3]).

%% - main=this
%% - active=non-idle cogs
%% - blocked=cogs with process blocked on future/resource
%% - idle=idle cogs
%% - clock_waiting=[{Min,Max,Task,Cog}]: processes with their cog waiting for
%%   simulated time to advance, with minimum and maximum waiting time.
%%   Ordered by ascending maximum waiting time such that the Max element of
%%   the head of the list is always MTE [Maximum Time Elapse]).
%% - dcs=list of deployment components
%% - timer=timeout callback before terminating program
%% - keepalive_after_clock_limit=should we kill all objects after clock limit
%%   has been reached y/n (used to keep state around when web server is active)
%%
%% Simulation ends when no cog is active or waiting for the clock / some
%% resources.  Note that a cog can be in "active" and "blocked" at the same
%% time.
-record(state,{main,active,blocked,idle,clock_waiting,dcs,timer,keepalive_after_clock_limit}).
%%External function

start_link(Main, Keepalive) ->
    gen_server:start_link({global, cog_monitor}, ?MODULE, [Main, Keepalive], []).

stop() ->
    gen_server:stop({global, cog_monitor}).

%% Waits until all cogs are idle
waitfor()->
    receive
        wait_done ->
            ok
    end.

%% Cogs interface
new_cog(Cog) ->
    gen_server:call({global, cog_monitor}, {cog,Cog,new}).

cog_active(Cog) ->
    gen_server:call({global, cog_monitor}, {cog,Cog,active}).

cog_blocked(Cog) ->
    gen_server:call({global, cog_monitor}, {cog, Cog, blocked}).

cog_unblocked(Cog) ->
    gen_server:call({global, cog_monitor}, {cog, Cog, unblocked}).

cog_idle(Cog) ->
    gen_server:call({global, cog_monitor}, {cog,Cog,idle}).

cog_died(Cog) ->
    gen_server:call({global, cog_monitor}, {cog,Cog,die}).

cog_blocked_for_clock(Task, Cog, Min, Max) ->
    gen_server:call({global, cog_monitor}, {cog,Task,Cog,clock_waiting,Min,Max}).

%% Tasks interface
task_waiting_for_clock(Task, Cog, Min, Max) ->
    gen_server:call({global, cog_monitor}, {task,Task,Cog,clock_waiting,Min,Max}).

%% Deployment Components interface
new_dc(DC) ->
    gen_server:call({global, cog_monitor}, {newdc, DC}).

dc_died(DC) ->
    gen_server:call({global, cog_monitor}, {dc_died, DC}).

get_dcs() ->
    gen_server:call({global, cog_monitor}, get_dcs).



%% gen_server callbacks

%%The callback gets as parameter the pid of the runtime process, which waits for all cogs to be idle
init([Main,Keepalive])->
    {ok,#state{main=Main,
               active=gb_sets:empty(),
               blocked=gb_sets:empty(),
               idle=gb_sets:empty(),
               clock_waiting=[],
               dcs=[],
               timer=undefined,
               keepalive_after_clock_limit=Keepalive}}.


handle_call({cog,Cog,new}, _From, State=#state{idle=I})->
    I1=gb_sets:add_element(Cog,I),
    {reply, ok, State#state{idle=I1}};
handle_call({cog,Cog,active}, _From, State=#state{active=A,idle=I,timer=T})->
    A1=gb_sets:add_element(Cog,A),
    I1=gb_sets:del_element(Cog,I),
    cancel(T),
    {reply, ok, State#state{active=A1,idle=I1,timer=undefined}};
handle_call({cog,Cog,idle}, _From, State=#state{active=A,idle=I})->
    A1=gb_sets:del_element(Cog,A),
    I1=gb_sets:add_element(Cog,I),
    S1=State#state{active=A1,idle=I1},
    case can_clock_advance(State, S1) of
        true->
            {reply, ok, advance_clock_or_terminate(S1)};
        false->
            {reply, ok, S1}
    end;
handle_call({cog,Cog,blocked}, _From, State=#state{active=A,blocked=B})->
    A1=gb_sets:del_element(Cog,A),
    B1=gb_sets:add_element(Cog,B),
    S1=State#state{active=A1,blocked=B1},
    case can_clock_advance(State, S1) of
        true->
            {reply, ok, advance_clock_or_terminate(S1)};
        false->
            {reply, ok, S1}
    end;
handle_call({cog,Cog,unblocked}, _From, State=#state{active=A,blocked=B, timer=T})->
    A1=gb_sets:add_element(Cog,A),
    B1=gb_sets:del_element(Cog,B),
    cancel(T),
    {reply, ok, State#state{active=A1,blocked=B1}};
handle_call({cog,Cog,die}, _From,State=#state{active=A,idle=I,blocked=B,clock_waiting=W})->
    A1=gb_sets:del_element(Cog,A),
    I1=gb_sets:del_element(Cog,I),
    B1=gb_sets:del_element(Cog,B),
    W1=lists:filter(fun ({_Min, _Max, _Task, Cog1}) ->  Cog1 =/= Cog end, W),
    S1=State#state{active=A1,idle=I1,blocked=B1,clock_waiting=W1},
    case can_clock_advance(State, S1) of
        true->
            {reply, ok, advance_clock_or_terminate(S1)};
        false->
            {reply, ok, S1}
    end;
handle_call({task,Task,Cog,clock_waiting,Min,Max}, _From,
             State=#state{clock_waiting=C}) ->
    C1=add_to_clock_waiting(C,{Min,Max,Task,Cog}),
    {reply, ok,State#state{clock_waiting=C1}};
handle_call({cog,Task,Cog,clock_waiting,Min,Max}, _From,
             State=#state{clock_waiting=C}) ->
    %% {cog, blocked} event comes separately
    C1=add_to_clock_waiting(C,{Min,Max,Task,Cog}),
    {reply, ok, State#state{clock_waiting=C1}};
handle_call({newdc, DC=#object{class=class_ABS_DC_DeploymentComponent}},
            _From, State=#state{dcs=DCs}) ->
    {reply, ok, State#state{dcs=[DC | DCs]}};
handle_call({dc_died, O}, _From, State=#state{dcs=DCs}) ->
    %% This event is not currently in use; we want DCs to stay alive for
    %% visualization.
    {reply, ok, State#state{dcs=lists:filter(fun (#object{ref=DC}) -> DC =/= O end, DCs)}};
handle_call(get_dcs, _From, State=#state{dcs=DCs}) ->
    {reply, DCs, State};
handle_call(Request, _From, State)->
    io:format("Unknown request: ~w~n", [Request]),
    {reply, error, State}.


handle_cast(_Request, State) ->
    %% unused
    {noreply, State}.


handle_info(_Info, State)->
    %% unused
    {noreply, State}.


terminate(_Reason,#state{timer=T})->
    cancel(T),
    ok.


code_change(_OldVsn, State, _Extra)->
    %% not supported
    {error, State}.

%%Private
cancel(undefined)->
    ok;
cancel(TRef)->
    {ok,cancel}=timer:cancel(TRef).

can_clock_advance(_OldState=#state{active=A, blocked=B},
                  _NewState=#state{active=A1, blocked=B1}) ->
    Old_idle = gb_sets:is_empty(gb_sets:subtract(A, B)),
    All_idle = gb_sets:is_empty(gb_sets:subtract(A1, B1)),
    not Old_idle and All_idle.

advance_clock_or_terminate(State=#state{main=M,active=A,clock_waiting=C,dcs=DCs,timer=T,keepalive_after_clock_limit=Keepalive}) ->
    case C of
        [] ->
            %% One last clock advance to finish the last resource period
            MTE=clock:distance_to_next_boundary(),
            clock:advance(MTE),
            lists:foreach(fun(DC) -> dc:update(DC, MTE) end, DCs),
            {ok,T1} = case T of
                          undefined -> timer:send_after(1000,M,wait_done);
                          _ -> {ok,T}
                      end,
            State#state{timer=T1};
        [{_Min, MTE, _Task, _Cog} | _] ->
            %% advance clock before waking up processes waiting for it
            Clockresult=clock:advance(MTE),
            case Clockresult of
                ok ->
                    lists:foreach(fun(DC) -> dc:update(DC, MTE) end, DCs),
                    {A1,C1}=lists:unzip(
                              lists:map(
                                fun(I) -> decrease_or_wakeup(MTE, I) end,
                                C)),
                    State#state{active=gb_sets:union(A, gb_sets:from_list(lists:flatten(A1))),
                                clock_waiting=lists:flatten(C1)};
                limit_reached ->
                    case Keepalive of
                        false ->
                            io:format("Simulation time limit reached; terminating~n", []),
                            Cogs=gb_sets:union([State#state.active, State#state.blocked, State#state.idle]),
                            gb_sets:fold(fun (Ref, ok) -> cog:stop_world(Ref) end, ok, Cogs),
                            gb_sets:fold(fun (Ref, ok) -> cog:kill_recklessly(Ref) end, ok, Cogs),
                            {ok,T1} = case T of
                                          undefined -> timer:send_after(1000,M,wait_done);
                                          _ -> {ok,T}
                                      end,
                            State#state{timer=T1};
                        true ->
                            io:format("Simulation time limit reached; terminate with Ctrl-C~n", []),
                            State
                    end
            end
    end .

decrease_or_wakeup(MTE, {Min, Max, Task, Cog}) ->
    %% Compute, for one entry in the clock_waiting queue, either a new entry
    %% with decreased deadline, or wake up the task and note the cog that
    %% should be re-added to the active set.  Note that we optimistically mark
    %% the cog as active: since it was idle before and we just unblocked a
    %% task, the cog will signal itself as active anyway as soon as the
    %% freshly-unblocked tasks gets around to telling it (and in the meantime,
    %% we might erroneously advance the clock a second time otherwise).
    case cmp:lt(MTE, Min) of
        true ->
            {[],
             {rationals:fast_sub(rationals:to_r(Min), rationals:to_r(MTE)),
              rationals:fast_sub(rationals:to_r(Max), rationals:to_r(MTE)),
              Task, Cog}};
        false ->
            Task ! {clock_finished, self()},
            receive
                {ok, Task} -> ok
            end,
            {Cog, []}
    end.

add_to_clock_waiting([H={_Min,Head,_Task,_Cog} | T], I={_Min1,Max,_Task1,_Cog1}) ->
    case rationals:is_greater(rationals:to_r(Head), rationals:to_r(Max)) of
        true -> [I, H | T];
        false -> [H | add_to_clock_waiting(T, I)]
    end;
add_to_clock_waiting([], I) ->
    [I].
