%%This file is licensed under the terms of the Modified BSD License.
-module(modelapi).

-behaviour(cowboy_handler).
-export([init/2, terminate/3]).
-record(state, {}).

-export([print_statistics/0]).


init(Req, _Opts) ->
    {Status, ContentType, Body} =
        %% routing for static files is handled elsewhere; see main_app.erl
        case cowboy_req:binding(request, Req, <<"default">>) of
            <<"default">> -> {200, <<"text/plain">>, <<"Hello Erlang!\n">>};
            <<"clock">> -> handle_clock();
            <<"dcs">> -> handle_dcs(cowboy_req:path_info(Req));
            <<"static_dcs">> -> handle_static_dcs(cowboy_req:path_info(Req));
            _ -> {404, <<"text/plain">>, <<"Not found">>}
        end,
    Req2 = cowboy_req:reply(Status, [{<<"content-type">>, ContentType}],
                            Body, Req),
    {ok, Req2, #state{}}.

handle_clock() ->
    {200, <<"text/plain">> , "Now: " ++ builtin:toString(null, clock:now()) ++ "\n" }.

handle_dcs([_Resource, _Filename]) ->
    {200, <<"application/json">>, get_statistics_json()}.

%% Convert into JSON integers instead of floats: erlang throws badarith when
%% the rationals get very large (test case: 5472206596936366950716234513847726699787633130083257868108935385073372521628474400544521868704326539544514945848761641723966834493669011242722490852350250920069840584545494657714176547830170076546766985189948190456085993999965841854043348210273114730931817418950948724982907640273166024155584846472815748114062887634396966520123600001491695765504058451726579037573091051607552055423198699302802395956790501740896358894471037106650700904924688637794684243427125018755079147845309097447199680 / 124463949580340014510986584728288862741803258927911335916878977240693013539088417076664562979601325740156255021810491719369575684864053619527117112327603062438063916198087526979602339983985468095190606013682227096265800975172556004113539099465524910539717462241015411708644965352863529428218264513501978734125515935814243527381509019662918750484272597804450713973581541009172909728477620791912354661828439825472308754606657629002302337966418841432399509232916424732092270353567444570118827)
%% Besides, we only want the number to display it in a graph, so the fraction
%% doesn't make a difference
abs_to_erl_number({N, 1}) -> N;
abs_to_erl_number({N, D}) -> N div D;
abs_to_erl_number(I) -> I.

abs_to_erl_list(dataNil) -> [];
abs_to_erl_list({dataCons, A, R}) -> [A | abs_to_erl_list(R)].

convert_number_list(List) ->
    lists:map(fun abs_to_erl_number/1, lists:reverse(abs_to_erl_list(List))).

create_history_list({dataTime, StartingTime}, History, Totalhistory) ->
    History2 = convert_number_list(History),
    Totalhistory2 = convert_number_list(Totalhistory),
    StartingTime2 = abs_to_erl_number(StartingTime),
    Length = length(History2),
    Indices=lists:seq(StartingTime2, StartingTime2 + Length - 1),
    case Length == length(Totalhistory2) of
        true -> lists:zipwith3(fun (Ind, His, Tot) -> [Ind, His, Tot] end,
                               Indices, History2, Totalhistory2);
        false -> lists:zipwith(fun (Ind, His) -> [Ind, His] end,
                               Indices, History2)
    end.


get_statistics_json() ->
    DCs = cog_monitor:get_dcs(),
    DC_infos=lists:map(fun (X) -> dc:get_resource_history(X, cpu) end, DCs),
    DC_info_json=lists:map(fun([Description, CreationTime, History, Totalhistory]) ->
                                   [{list_to_binary("name"), list_to_binary(Description)},
                                    {list_to_binary("values"), create_history_list(CreationTime, History, Totalhistory)}]
                           end, DC_infos),
    io_lib:format("Deployment components:~n~w~n",
                  [jsx:encode(DC_info_json)]),
    jsx:encode(DC_info_json).


handle_static_dcs([]) ->
    {200, <<"text/plain">> , get_statistics() }.


get_statistics() ->
    DCs = cog_monitor:get_dcs(),
    DC_infos=lists:flatten(lists:map(fun dc:get_description/1, DCs)),
    io_lib:format("Deployment components:~n~s~n", [DC_infos]).

terminate(_Reason, _Req, _State) ->
    ok.

print_statistics() ->
    io:format("~s", [get_statistics()]),
    ok.
