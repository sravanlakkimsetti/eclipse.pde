package org.eclipse.pde.internal.ui.neweditor.plugin;

import java.util.*;
import org.eclipse.jdt.core.*;
import org.eclipse.jface.viewers.*;
import org.eclipse.pde.internal.ui.*;
import org.eclipse.swt.widgets.*;
import org.eclipse.ui.dialogs.*;

/**
 * @author melhem
 *
 */
public class PackageSelectionDialog extends ElementListSelectionDialog {

	public static final String PACKAGE_MESSAGE = "PackageSelectionDialog.label";
	
	public PackageSelectionDialog(Shell parent, ILabelProvider renderer, IJavaProject jProject) {
		this(parent, renderer, jProject, new Vector());
	}
	/**
	 * @param parent
	 * @param renderer
	 */
	public PackageSelectionDialog(Shell parent, ILabelProvider renderer, IJavaProject jProject, Vector existingPackages) {
		super(parent, renderer);
		setElements(jProject, existingPackages);
		setMultipleSelection(true);
		setMessage(PDEPlugin.getResourceString(PACKAGE_MESSAGE));
	}
	/**
	 * 
	 */
	private void setElements(IJavaProject jProject, Vector existingPackages) {
		ArrayList list = new ArrayList();
		try {
			IPackageFragmentRoot[] roots = getRoots(jProject);
			for (int i = 0; i < roots.length; i++) {
				IJavaElement[] children = roots[i].getChildren();
				for (int j = 0; j < children.length; j++) {
					IPackageFragment fragment = (IPackageFragment)children[j];
					if (fragment.hasChildren() && !existingPackages.contains(fragment.getElementName()))
						list.add(fragment);
				}
			}
		} catch (JavaModelException e) {
		}
		setElements(list.toArray());
	}
	
	private IPackageFragmentRoot[] getRoots(IJavaProject jProject) {
		ArrayList result = new ArrayList();
		try {
			IPackageFragmentRoot[] roots = jProject.getPackageFragmentRoots();
			for (int i = 0; i < roots.length; i++) {
				if (roots[i].getKind() == IPackageFragmentRoot.K_SOURCE
						|| (roots[i].isArchive() && !roots[i].isExternal())) {
					result.add(roots[i]);
				}
			}
		} catch (JavaModelException e) {
		}
		return (IPackageFragmentRoot[])result.toArray(new IPackageFragmentRoot[result.size()]);	
	}
	
	/* (non-Javadoc)
	 * @see org.eclipse.ui.dialogs.ElementListSelectionDialog#createDialogArea(org.eclipse.swt.widgets.Composite)
	 */
	protected Control createDialogArea(Composite parent) {
		Control control = super.createDialogArea(parent);
		getShell().setText("Package Selection");
		return control;
	}
}
