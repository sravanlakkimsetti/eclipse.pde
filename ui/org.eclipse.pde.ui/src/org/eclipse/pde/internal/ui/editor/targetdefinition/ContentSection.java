/*******************************************************************************
 * Copyright (c) 2005, 2009 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.pde.internal.ui.editor.targetdefinition;

import java.lang.reflect.InvocationTargetException;
import org.eclipse.core.runtime.*;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.jface.operation.IRunnableWithProgress;
import org.eclipse.pde.internal.core.target.provisional.ITargetDefinition;
import org.eclipse.pde.internal.ui.PDEPlugin;
import org.eclipse.pde.internal.ui.PDEUIMessages;
import org.eclipse.pde.internal.ui.editor.FormLayoutFactory;
import org.eclipse.pde.internal.ui.shared.target.BundleContainerTable;
import org.eclipse.pde.internal.ui.shared.target.IBundleContainerTableReporter;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.ui.forms.SectionPart;
import org.eclipse.ui.forms.editor.FormPage;
import org.eclipse.ui.forms.widgets.*;

/**
 * Section for editing the content of the target (bundle containers) in the target definition editor
 * @see DefinitionPage
 * @see TargetEditor
 */
public class ContentSection extends SectionPart {

	private BundleContainerTable fTable;
	private TargetEditor fEditor;

	public ContentSection(FormPage page, Composite parent) {
		super(parent, page.getManagedForm().getToolkit(), Section.DESCRIPTION | ExpandableComposite.TITLE_BAR);
		fEditor = (TargetEditor) page.getEditor();
		createClient(getSection(), page.getEditor().getToolkit());
	}

	/**
	 * @return The target model backing this editor
	 */
	private ITargetDefinition getTarget() {
		return fEditor.getTarget();
	}

	/**
	 * Creates the UI for this section.
	 * 
	 * @param section section the UI is being added to
	 * @param toolkit form toolkit used to create the widgets
	 */
	protected void createClient(Section section, FormToolkit toolkit) {
		section.setLayout(FormLayoutFactory.createClearTableWrapLayout(false, 1));
		GridData sectionData = new GridData(GridData.FILL_BOTH);
		sectionData.horizontalSpan = 2;
		section.setLayoutData(sectionData);
		section.setText(PDEUIMessages.ContentSection_0);

		section.setDescription(PDEUIMessages.ContentSection_1);
		Composite client = toolkit.createComposite(section);
		client.setLayout(FormLayoutFactory.createSectionClientGridLayout(false, 1));
		client.setLayoutData(new GridData(GridData.FILL_BOTH | GridData.GRAB_VERTICAL));

		fTable = BundleContainerTable.createTableInForm(client, toolkit, new IBundleContainerTableReporter() {
			public void runResolveOperation(final IRunnableWithProgress operation) {
				Job job = new Job(PDEUIMessages.TargetDefinitionContentPage_0) {
					protected IStatus run(IProgressMonitor monitor) {
						try {

							operation.run(monitor);
							return Status.OK_STATUS;
						} catch (InvocationTargetException e) {
							return new Status(IStatus.ERROR, PDEPlugin.getPluginId(), PDEUIMessages.TargetDefinitionContentPage_5, e);
						} catch (InterruptedException e) {
							return Status.CANCEL_STATUS;
						}
					}
				};
				job.setUser(true);
				job.schedule();
			}

			public void contentsChanged() {
				markDirty();
			}
		});

		toolkit.paintBordersFor(client);

		section.setClient(client);
	}

	/* (non-Javadoc)
	 * @see org.eclipse.ui.forms.AbstractFormPart#refresh()
	 */
	public void refresh() {
		fTable.setInput(getTarget());
		super.refresh();
	}

}
