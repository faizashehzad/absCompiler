package eu.hatsproject.absplugin.navigator;

import java.util.ArrayList;

import static eu.hatsproject.absplugin.util.UtilityFunctions.getAbsNature;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.jface.text.ITextSelection;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.TreePath;
import org.eclipse.jface.viewers.TreeSelection;
import org.eclipse.ui.IEditorDescriptor;
import org.eclipse.ui.IEditorInput;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.PartInitException;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.navigator.ILinkHelper;
import org.eclipse.ui.part.FileEditorInput;
import org.eclipse.ui.texteditor.ITextEditor;

import abs.frontend.ast.CompilationUnit;
import abs.frontend.ast.ModuleDecl;
import eu.hatsproject.absplugin.builder.AbsNature;
import eu.hatsproject.absplugin.util.InternalASTNode;
import eu.hatsproject.absplugin.util.UtilityFunctions;
import eu.hatsproject.absplugin.util.UtilityFunctions.EditorPosition;

/**
 * Class for providing 'link with editor support' for the ABSNavigator 
 * @author cseise
 *
 */
public class LinkHelper implements ILinkHelper {

	/**
	 * {@inheritDoc}
	 */
	@Override
	public IStructuredSelection findSelection(IEditorInput anInput) {
		if (anInput instanceof FileEditorInput) {
			FileEditorInput fileInput = (FileEditorInput) anInput;
			IFile file = fileInput.getFile();
			
			IProject project = getProject(fileInput);
			AbsNature nature = getAbsNature(project);

			//If we did not get back an ABS nature, do nothing.
			if (nature == null) {
				return null;
			}	
			
			ITextEditor editor = getABSEditor(anInput);
		
			ModuleDecl md = getModuleDeclAtCurrentCursor(file, nature, editor);

			return buildTreeSelection(project, InternalASTNode.wrapASTNode(md,nature));
		}
		return null;
	}

	private ModuleDecl getModuleDeclAtCurrentCursor(IFile file, AbsNature nature, ITextEditor editor) {
		ModuleDecl md = null;
		
		synchronized (nature.modelLock) {
			if (nature.getCompilationUnit(file).getNumModuleDecl() > 0){
				//get start line of editor selection
				ITextSelection selection = (ITextSelection) editor.getSelectionProvider().getSelection();
				int startLine = selection.getStartLine();
				
				CompilationUnit cu = nature.getCompilationUnit(file);
				
				//find the module which the selection is located in...
				for (ModuleDecl m : cu.getModuleDecls()){
					EditorPosition position = UtilityFunctions.getPosition(m);
					int moduleStartLine = position.getLinestart();
					int moduleEndLine = position.getLineend();
					if (startLine >= moduleStartLine && startLine <= moduleEndLine){
						md = m;
						break;
					}
					
				}
				
				//if no module was found or no selection was made, take the first one in the compilation unit as fallback
				if (md == null){
					md = cu.getModuleDecl(0);
				}
			}
		}
		return md;
	}

	private ITextEditor getABSEditor(IEditorInput anInput) {
		//Get the text editor of the file input
		ITextEditor editor;
		IEditorDescriptor desc = 
			PlatformUI.
			getWorkbench().
			getEditorRegistry().
			getDefaultEditor(anInput.getName());
		try {
			 editor = 
				 (ITextEditor) PlatformUI.
				 getWorkbench().
				 getActiveWorkbenchWindow().
				 getActivePage().
				 openEditor(anInput, desc.getId());
		} catch (PartInitException e) {
			editor = null;
		}
		return editor;
	}

	/**
	 * Build up a {@link TreeSelection} suitable for the ABSNavigator from the
	 * given component
	 * 
	 * @param project
	 *            IProject The according project of the ModuleDecl.
	 * @param md
	 *            ModuleDecl The target ModuleDecl to highlight.
	 * @return A TreeSelection including a {@link TreePath} from project to md
	 *         or null if md or project is null, or if no ABSnature can be
	 *         retrieved from project
	 */
	private static TreeSelection buildTreeSelection(IProject project, InternalASTNode<ModuleDecl> md) {
		if (project != null && md != null) {
			AbsNature nature = UtilityFunctions.getAbsNature(project);

			if (nature != null) {
				// Split the module's name and return the module hierarchy
				ArrayList<ModulePath> paths = NavigatorUtils.getParentHierarchyForModuleDecl(md.getASTNode(), nature);
				
				ArrayList<Object> arli = new ArrayList<Object>();
				arli.add(project);
				
				
				if (paths.size() > 0){
				ModulePath lastElement = paths.get(paths.size()-1);
				arli.addAll(paths);
				if (!(lastElement.hasModule() && lastElement.getModulePath().equals(md.getASTNode().getName()))){
					arli.add(md);
				}

				}else{
					if (NavigatorUtils.hasSubModules(md)){
						arli.add(new ModulePath(nature, md.getASTNode().getName()));
					}else{
						arli.add(md);
					}
				}
				TreePath path = new TreePath(arli.toArray());
				TreeSelection ts = new TreeSelection(new TreePath[] { path });
				return ts;
			} else {
				return null;
			}
		}
		return null;
	}
	
	private IProject getProject(FileEditorInput anInput){	
		IProject project = anInput.getFile().getProject();
		return project;
	}

	@Override
	public void activateEditor(IWorkbenchPage aPage, IStructuredSelection aSelection) {
		if (aSelection instanceof TreeSelection) {
			TreeSelection ts = (TreeSelection) aSelection;
			try {
				NavigatorUtils.openEditor(ts);
			} catch (PartInitException e) {
				UtilityFunctions.showErrorMessage("Fatal error in ABS Navigator View:\n Could not open the editor connected to the selected tree element.");
			}
		}
	}

}