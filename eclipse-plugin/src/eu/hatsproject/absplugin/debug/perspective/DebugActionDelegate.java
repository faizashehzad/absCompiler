package eu.hatsproject.absplugin.debug.perspective;

import static eu.hatsproject.absplugin.debug.DebugUtils.executeNSteps;
import static eu.hatsproject.absplugin.debug.DebugUtils.executeSingleStep;
import static eu.hatsproject.absplugin.debug.DebugUtils.resume;
import static eu.hatsproject.absplugin.debug.DebugUtils.runToLine;
import static eu.hatsproject.absplugin.debug.DebugUtils.saveHistory;
import static eu.hatsproject.absplugin.debug.DebugUtils.stepOver;
import static eu.hatsproject.absplugin.debug.DebugUtils.terminate;
import static eu.hatsproject.absplugin.debug.DebugUtils.selectScheduler;
import static eu.hatsproject.absplugin.util.Constants.DEBUG_BUTTON_HISTORY_ID;
import static eu.hatsproject.absplugin.util.Constants.DEBUG_BUTTON_NSTEPS_ID;
import static eu.hatsproject.absplugin.util.Constants.DEBUG_BUTTON_RESUME_ID;
import static eu.hatsproject.absplugin.util.Constants.DEBUG_BUTTON_RUN_TO_LINE_ID;
import static eu.hatsproject.absplugin.util.Constants.DEBUG_BUTTON_STEP_ID;
import static eu.hatsproject.absplugin.util.Constants.DEBUG_BUTTON_STEP_OVER_ID;
import static eu.hatsproject.absplugin.util.Constants.DEBUG_BUTTON_TERMINATE_ID;
import static eu.hatsproject.absplugin.util.Constants.DEBUG_BUTTON_SCHEDULER_CHOICE_ID;

import java.io.File;

import org.eclipse.jface.action.IAction;
import org.eclipse.jface.dialogs.Dialog;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.FileDialog;
import org.eclipse.ui.IActionDelegate2;
import org.eclipse.ui.IViewActionDelegate;
import org.eclipse.ui.IViewPart;

import eu.hatsproject.absplugin.debug.DebugUtils;

/**
 * Class handling clicks on buttons in the ABS debug view.
 * @author tfischer
 */
public class DebugActionDelegate implements IViewActionDelegate, IActionDelegate2{

	@Override
	public void run(IAction action) {
		if(action == saveHistory){
			FileDialog historyDialog = new FileDialog(Display.getCurrent().getActiveShell(), SWT.SAVE);
			String pathString = historyDialog.open();
			if(pathString != null){
				File path = new File(pathString);
				DebugUtils.getSchedulerRef().saveHistory(path);
			}
		} else if(action == executeSingleStep){
			DebugUtils.getSchedulerRef().doSingleStep();
		} else if(action == executeNSteps){
			Dialog stepDialog = new StepNumberDialog(Display.getCurrent().getActiveShell());
			stepDialog.open();
		} else if(action == stepOver){
			DebugUtils.getSchedulerRef().doStepOver();
		} else if(action == runToLine){
			Dialog lineDialog = new LineNumberDialog(Display.getCurrent().getActiveShell());
			lineDialog.open();
		} else if(action == resume){
			DebugUtils.getSchedulerRef().resumeAutomaticScheduling();
		} else if(action == terminate){
			DebugUtils.getDebugger().shutdown();
		}
	}

	@Override
	public void selectionChanged(IAction action, ISelection selection) {}
	@Override
	public void dispose() {}
	@Override
	public void init(IViewPart arg0) {}

	/**
	 * Saves all Actions from the perspective in eu.hatsproject.absplugin.debug.DebugUtils for further usage.
	 */
	@Override
	public void init(IAction action) {
		//Initialize individual buttons
		if(action.getId().equals(DEBUG_BUTTON_HISTORY_ID)){
			saveHistory = action;
			saveHistory.setEnabled(false);
		} else if(action.getId().equals(DEBUG_BUTTON_STEP_ID)){
			executeSingleStep = action;
			executeSingleStep.setEnabled(false);
		} else if(action.getId().equals(DEBUG_BUTTON_NSTEPS_ID)){
			executeNSteps = action;
			executeNSteps.setEnabled(false);
		} else if(action.getId().equals(DEBUG_BUTTON_RESUME_ID)){
			resume = action;
			resume.setEnabled(false);
		} else if(action.getId().equals(DEBUG_BUTTON_STEP_OVER_ID)){
			stepOver = action;
			stepOver.setEnabled(false);
		} else if(action.getId().equals(DEBUG_BUTTON_RUN_TO_LINE_ID)){
			runToLine = action;
			runToLine.setEnabled(false);
		} else if(action.getId().equals(DEBUG_BUTTON_TERMINATE_ID)){
			terminate = action;
			terminate.setEnabled(false);
		} else if(action.getId().equals(DEBUG_BUTTON_SCHEDULER_CHOICE_ID)){
			selectScheduler = action;
			selectScheduler.setEnabled(false);
		}
	}

	@Override
	public void runWithEvent(IAction action, Event event) {
		run(action);
	}
}