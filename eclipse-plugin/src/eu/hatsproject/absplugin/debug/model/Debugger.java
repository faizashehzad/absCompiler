/** 
 * Copyright (c) 2009-2011, The HATS Consortium. All rights reserved. 
 * This file is licensed under the terms of the Modified BSD License.
 */
package eu.hatsproject.absplugin.debug.model;

import static eu.hatsproject.absplugin.debug.DebugUtils.getDebugViewer;
import static eu.hatsproject.absplugin.debug.DebugUtils.getDebugger;
import static eu.hatsproject.absplugin.util.Constants.ABSDEBUGPERSPECTIVE_ID;
import static eu.hatsproject.absplugin.util.Constants.DO_DEBUG;
import static eu.hatsproject.absplugin.util.UtilityFunctions.showErrorMessage;
import static eu.hatsproject.absplugin.util.UtilityFunctions.standardExceptionHandling;


import java.util.HashMap;

import org.eclipse.core.runtime.Path;
import org.eclipse.swt.widgets.Display;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.WorkbenchException;

import abs.backend.java.debugging.DebugModel;
import abs.backend.java.lib.runtime.ABSRuntime;
import abs.backend.java.observing.COGView;
import abs.backend.java.observing.ObjectView;
import abs.backend.java.observing.SystemObserver;
import abs.backend.java.scheduling.TotalSchedulingStrategy;
import eu.hatsproject.absplugin.debug.DebugUtils;
import eu.hatsproject.absplugin.debug.scheduling.SchedulingStrategy;

/**
 * The Debugger handles start and shutdown of a debug process (runtime) and delegates basic events
 * from the runtime.
 * @author fstrauss, tfischer
 */
public class Debugger implements SystemObserver{
	private DebugModel model;
	private ABSRuntime runtime;
	private String projectName = "none";
	
	private HashMap<COGView, Tasks> tasks = new HashMap<COGView, Tasks>();
	private HashMap<COGView, Objects> objects = new HashMap<COGView, Objects>();
	

	/**
	 * Shuts down the previous debug process and initializes the debugger with parameters 
	 * for a new debug process.
	 * @param useOurSystemObserver indicate if the internal debug perspective should be used
	 * @param projectName
	 * @param r
	 * @param debuggerRunner
	 */
	public void initDebugger(String projectName, ABSRuntime r, Thread debuggerRunner, boolean useOurSystemObserver){
		shutdown();
		
		this.projectName = projectName;
		this.runtime = r;
		if(useOurSystemObserver){
			r.addSystemObserver(this);
		}
    	
		model = new DebugModel();
		model.registerListener(new DebugModelListener());
        debuggerRunner.start();

        getDebugViewer().setInput(new Object[] {model});
	
        if(DebugUtils.getSchedulerRef() != null) 
        	DebugUtils.getSchedulerRef().updateScheduler();
	}
	
	/**
	 * Used to start the ABS Runtime. If an earlier Runtime is present, it is shut down first.
	 * 
	 * @param projectName
	 * @param mainClassName
	 * @param genPath
	 * @param debuggerArgsSystemObserver
	 * @param debuggerArgsTotalScheduler
	 * @param debuggerIsInDebugMode
	 * @param debuggerArgsRandomSeed
	 */
	public static void startABSRuntime(final String projectName,
			final String mainClassName, final Path genPath, String debuggerArgsSystemObserver,
			String debuggerArgsTotalScheduler, boolean debuggerIsInDebugMode, String debuggerArgsRandomSeed) {
		
		if(DO_DEBUG) System.out.println("start internal debugger");
		
        final ABSRuntime r = new ABSRuntime();
        if(debuggerIsInDebugMode) r.enableDebugging(true);

        boolean useOurScheduling = addSchedulingStrategy(debuggerArgsTotalScheduler, r);
        final boolean useOurSystemObserver = addSystemObservers(debuggerArgsSystemObserver, r);
        
        if(debuggerArgsRandomSeed != null && !debuggerArgsRandomSeed.isEmpty()){
        	String seedString = debuggerArgsRandomSeed.replace("-Dabs.randomseed=", "");
        	Long seedNumber = Long.valueOf(seedString);
        	r.setRandomSeed(seedNumber);
        }

        final Thread debuggerRunner = new Thread(new Runnable(){
        	@Override
			public void run() {
				try {
					if (DO_DEBUG){
						System.out.println("Start ABSRuntime .. ");
						System.out.println("path: " + genPath.toString());
						System.out.println("name:" + mainClassName);
					}
					r.start(genPath.toFile(), mainClassName);
				} catch (ClassNotFoundException e) {
					exceptionHandling(e);
				} catch (InstantiationException e) {
					exceptionHandling(e);
				} catch (IllegalAccessException e) {
					exceptionHandling(e);
				}
			}
        	
        	private void exceptionHandling(Exception e){
				standardExceptionHandling(e);
				showErrorMessage("Not able to start ABSRuntime");
        		
        	}
        });
        
        if (useOurScheduling) {
    		Display.getDefault().asyncExec(new Runnable() {
    			@Override
				public void run() {
    				try {
    					PlatformUI.getWorkbench().showPerspective(ABSDEBUGPERSPECTIVE_ID, PlatformUI.getWorkbench().getActiveWorkbenchWindow());
    					getDebugger().initDebugger(projectName, r, debuggerRunner, useOurSystemObserver);
    				} catch (WorkbenchException e) {
    					standardExceptionHandling(e);
    					showErrorMessage("Could not open ABS debug perspective");
    				}
    			}});
        } else {
           debuggerRunner.start();
        }
	
	}

	/**
	 * Terminate the current ABSRuntime, if one exists.
	 */
	public void shutdown(){
		if(isRunning()){
			runtime.shutdown();
			runtime = null;
		}
		DebugUtils.getSchedulerRef().systemFinished();
		DebugUtils.removeAllHighlighting();
		Display.getDefault().asyncExec(new Runnable() {

			@Override
			public void run() {
				DebugUtils.refreshButtonEnablement();
				DebugUtils.getDebugViewer().refresh();
				
			}
		});
	}

	/**
	 * Checks, if the Debugger currently has a runtime.
	 */
	public boolean isRunning(){
		return runtime != null;
	}
	
	private static boolean addSchedulingStrategy(String debuggerArgsScheduler, final ABSRuntime r) {
		if(debuggerArgsScheduler != null && !debuggerArgsScheduler.isEmpty()){
			String scheduler = debuggerArgsScheduler.replaceFirst("-Dabs.totalscheduler=", "");
            if(!scheduler.isEmpty()){
				try {
					Class<?> c = Class.forName(scheduler);
	            	Object o = c.newInstance();
	            	TotalSchedulingStrategy ss = (TotalSchedulingStrategy) o;
	                r.setTotalSchedulingStrategy(ss);
	                return (ss instanceof SchedulingStrategy);
				} catch (ClassNotFoundException e) {
					standardExceptionHandling(e);
					showErrorMessage("Not able to instantiate total scheduler");
				} catch (InstantiationException e) {
					standardExceptionHandling(e);
					showErrorMessage("Not able to instantiate total scheduler");
				} catch (IllegalAccessException e) {
					standardExceptionHandling(e);
					showErrorMessage("Not able to instantiate total scheduler");
				}
            } 
        }
		return false;
	}
	
	private static boolean addSystemObservers(String debuggerArgsSystemObserver, final ABSRuntime r) {
		boolean useThisSystemObserver = false;
		if(debuggerArgsSystemObserver != null && !debuggerArgsSystemObserver.isEmpty()){
			String systemObserverArgs = debuggerArgsSystemObserver.replaceFirst("-Dabs.systemobserver=", "");
			String[] systemObservers = systemObserverArgs.split(",");
			
			for (String observer : systemObservers) {
	            if(!observer.isEmpty()){
					try {
						Class<?> c = Class.forName(observer);
		            	Object o = c.newInstance();
		            	SystemObserver so = (SystemObserver) o;
		            	if(so instanceof Debugger){
		            		useThisSystemObserver = true;
		            	} else {
		            		r.addSystemObserver(so);
		            	}
		            	//FIXME do not close eclipse
					} catch (ClassNotFoundException e) {
						//skip
						e.printStackTrace();
					} catch (InstantiationException e) {
						//skip
						e.printStackTrace();
					} catch (IllegalAccessException e) {
						//skip
						e.printStackTrace();
					}
	            }
				
			}
        }
		return useThisSystemObserver;
	}
	
	@Override
	public void systemStarted() {}

	@Override
	public void systemFinished() {
		shutdown();
	}
	
	@Override
	public void newCOGCreated(COGView cog, ObjectView initialObject) {
		cog.registerObjectCreationListener(new ObjectCreationObserver());
		model.cogCreated(cog, initialObject);
	}
	
	public DebugModel getModel(){
		return model;
	}	
	
	/**
	 * Returns an {@link eu.hatsproject.absplugin.debug.model.Tasks} object related to a given COGView.
	 * These objects are necessary to group tasks in the tree viewer of the debug tree. Tasks objects
	 * are stored in a hash map by the debugger.
	 * @see eu.hatsproject.absplugin.debug.views.debugview.DebugTreeContentProvider
	 * @param cog COGView for which the Tasks object shall be returned 
	 * @return The Tasks object related to the given COGView
	 */
	public Tasks getTasks(COGView cog){
		if(tasks.containsKey(cog)){
			return tasks.get(cog);
		} else{
			Tasks t = new Tasks(cog);
			tasks.put(cog, t);
			return t;
		}
	}
	
	/**
	 * Returns an {@link eu.hatsproject.absplugin.debug.model.Objects} object related to a given COGView.
	 * These objects are necessary to group objects in the tree viewer of the debug tree. Objects objects
	 * are stored in a hash map by the debugger.
	 * @see eu.hatsproject.absplugin.debug.views.debugview.DebugTreeContentProvider
	 * @param cog COGView for which the Objects object shall be returned
	 * @return The Objects object related to the given COGView
	 */
	public Objects getObjects(COGView cog){
		if(objects.containsKey(cog)){
			return objects.get(cog);
		} else{
			Objects o = new Objects(cog);
			o.addObject(model.getCOGInfo(cog).getInitialObject());
			objects.put(cog, o);
			return o;
		}
	}

	/**
	 * @return Name of the project which is currently debugged.
	 */
	public String getProjectName(){
		return projectName;
	}

}
