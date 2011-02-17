package eu.hatsproject.absplugin.navigator.actionProvider;

import static eu.hatsproject.absplugin.navigator.NavigatorUtils.openEditor;

import org.eclipse.jface.action.Action;
import org.eclipse.jface.action.IAction;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.ISelectionChangedListener;
import org.eclipse.jface.viewers.SelectionChangedEvent;
import org.eclipse.jface.viewers.TreeSelection;
import org.eclipse.ui.PartInitException;

/**
 * Class for providing double click support for ASTNodes and ModulePaths in the PackageExplorer
 * @author cseise
 *
 */
public class DoubleClickAction extends Action implements ISelectionChangedListener, IAction {
	private ISelection selection;
	
	@Override
	public void selectionChanged(SelectionChangedEvent event) {
		selection = event.getSelection();		
	}
	
	@Override
	public void run(){
		if (selection != null && selection instanceof TreeSelection){
			try {
				openEditor((TreeSelection)selection);
			} catch (PartInitException e) {
				e.printStackTrace();
			}
		}
	}


}