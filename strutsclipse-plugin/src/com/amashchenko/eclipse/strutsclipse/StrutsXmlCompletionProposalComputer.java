/*
 * Copyright 2015 Aleksandr Mashchenko.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.amashchenko.eclipse.strutsclipse;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IResourceVisitor;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jdt.ui.text.java.CompletionProposalComparator;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.IRegion;
import org.eclipse.jface.text.contentassist.CompletionProposal;
import org.eclipse.jface.text.contentassist.ICompletionProposal;
import org.eclipse.jface.text.contentassist.IContextInformation;
import org.eclipse.ui.editors.text.TextFileDocumentProvider;
import org.eclipse.ui.texteditor.IDocumentProvider;
import org.eclipse.wst.common.componentcore.ComponentCore;
import org.eclipse.wst.common.componentcore.resources.IVirtualComponent;
import org.eclipse.wst.common.componentcore.resources.IVirtualFolder;
import org.eclipse.wst.sse.ui.contentassist.CompletionProposalInvocationContext;
import org.eclipse.wst.sse.ui.contentassist.ICompletionProposalComputer;

import com.amashchenko.eclipse.strutsclipse.java.ActionMethodProposalComparator;
import com.amashchenko.eclipse.strutsclipse.java.JavaClassCompletion;
import com.amashchenko.eclipse.strutsclipse.xmlparser.ElementRegion;
import com.amashchenko.eclipse.strutsclipse.xmlparser.StrutsXmlParser;
import com.amashchenko.eclipse.strutsclipse.xmlparser.TagRegion;
import com.amashchenko.eclipse.strutsclipse.xmlparser.TilesXmlParser;

public class StrutsXmlCompletionProposalComputer implements
		ICompletionProposalComputer {
	private static final List<String> DISPATCHER_EXTENSIONS = Arrays
			.asList(new String[] { "jsp", "html", "htm" });
	private static final List<String> FREEMARKER_EXTENSIONS = Arrays
			.asList(new String[] { "ftl" });

	private final StrutsXmlParser strutsXmlParser;
	private final TilesXmlParser tilesXmlParser;

	private final CompletionProposalComparator proposalComparator;
	private final ActionMethodProposalComparator methodProposalComparator;

	public StrutsXmlCompletionProposalComputer() {
		strutsXmlParser = new StrutsXmlParser();
		tilesXmlParser = new TilesXmlParser();
		proposalComparator = new CompletionProposalComparator();
		proposalComparator.setOrderAlphabetically(true);
		methodProposalComparator = new ActionMethodProposalComparator();
	}

	@Override
	public List<ICompletionProposal> computeCompletionProposals(
			CompletionProposalInvocationContext context,
			IProgressMonitor monitor) {
		final TagRegion tagRegion = strutsXmlParser.getTagRegion(
				context.getDocument(), context.getInvocationOffset());

		String[][] proposals = null;
		IRegion proposalRegion = null;
		String elementValuePrefix = null;
		String elementValue = null;
		String multiValueSeparator = null;
		boolean sortProposals = false;

		if (tagRegion != null && tagRegion.getCurrentElement() != null) {
			final String elementName = tagRegion.getCurrentElement().getName();
			proposalRegion = tagRegion.getCurrentElement().getValueRegion();
			elementValuePrefix = tagRegion.getCurrentElementValuePrefix();
			elementValue = tagRegion.getCurrentElement().getValue();

			if (StrutsXmlConstants.PACKAGE_TAG.equalsIgnoreCase(tagRegion
					.getName())) {
				if (StrutsXmlConstants.EXTENDS_ATTR
						.equalsIgnoreCase(elementName)) {
					Set<String> packageNames = strutsXmlParser
							.getPackageNames(context.getDocument());

					proposals = new String[StrutsXmlConstants.DEFAULT_PACKAGE_NAMES.length
							+ packageNames.size()][2];

					for (int i = 0; i < StrutsXmlConstants.DEFAULT_PACKAGE_NAMES.length; i++) {
						proposals[i][0] = StrutsXmlConstants.DEFAULT_PACKAGE_NAMES[i][0];
					}
					int indx = StrutsXmlConstants.DEFAULT_PACKAGE_NAMES.length;
					for (String p : packageNames) {
						proposals[indx++][0] = p;
					}
					// extends attribute can have multiple values separated by ,
					multiValueSeparator = ",";
				}
			} else if (StrutsXmlConstants.CONSTANT_TAG
					.equalsIgnoreCase(tagRegion.getName())) {
				if (StrutsXmlConstants.NAME_ATTR.equalsIgnoreCase(elementName)) {
					proposals = StrutsXmlConstants.DEFAULT_CONSTANTS;
				}
			} else if (StrutsXmlConstants.ACTION_TAG.equalsIgnoreCase(tagRegion
					.getName())) {
				if (StrutsXmlConstants.NAME_ATTR.equalsIgnoreCase(elementName)
						|| StrutsXmlConstants.METHOD_ATTR
								.equalsIgnoreCase(elementName)) {
					final ElementRegion classAttr = tagRegion.getAttrs().get(
							StrutsXmlConstants.CLASS_ATTR);

					if (classAttr == null) {
						proposals = StrutsXmlConstants.DEFAULT_METHODS;
					} else {
						List<ICompletionProposal> methodProposals = JavaClassCompletion
								.getActionMethodProposals(elementValuePrefix,
										classAttr.getValue(),
										context.getDocument(), proposalRegion);
						// sort
						Collections.sort(methodProposals,
								methodProposalComparator);

						// return proposals
						return methodProposals;
					}
				} else if (StrutsXmlConstants.CLASS_ATTR
						.equalsIgnoreCase(elementName)) {
					// return proposals
					return JavaClassCompletion.getSimpleJavaProposals(
							elementValuePrefix, context.getDocument(),
							proposalRegion);
				}
			} else if (StrutsXmlConstants.RESULT_TAG.equalsIgnoreCase(tagRegion
					.getName())) {
				if (StrutsXmlConstants.NAME_ATTR.equalsIgnoreCase(elementName)) {
					proposals = StrutsXmlConstants.DEFAULT_RESULT_NAMES;
				} else if (StrutsXmlConstants.TYPE_ATTR
						.equalsIgnoreCase(elementName)) {
					proposals = StrutsXmlConstants.DEFAULT_RESULT_TYPES;
				} else if (elementName == null) { // result tag body
					final ElementRegion typeAttr = tagRegion.getAttrs().get(
							StrutsXmlConstants.TYPE_ATTR);
					proposals = computeResultBodyProposals(
							context.getDocument(),
							context.getInvocationOffset(),
							typeAttr == null ? null : typeAttr.getValue());
					sortProposals = true;
				}
			} else if (StrutsXmlConstants.PARAM_TAG.equalsIgnoreCase(tagRegion
					.getName())) {
				if (elementName == null) { // param tag body
					final ElementRegion nameAttr = tagRegion.getAttrs().get(
							StrutsXmlConstants.NAME_ATTR);
					if (nameAttr != null) {
						final TagRegion parentResultTagRegion = strutsXmlParser
								.getParentTagRegion(context.getDocument(),
										context.getInvocationOffset(),
										StrutsXmlConstants.RESULT_TAG);
						if (parentResultTagRegion != null) {
							final ElementRegion typeAttr = parentResultTagRegion
									.getAttrs().get(
											StrutsXmlConstants.TYPE_ATTR);
							boolean correctTypeAndName = (StrutsXmlConstants.LOCATION_PARAM
									.equals(nameAttr.getValue()) && (typeAttr == null || !StrutsXmlConstants.REDIRECT_ACTION_RESULT
									.equals(typeAttr.getValue())))
									|| (typeAttr != null
											&& StrutsXmlConstants.REDIRECT_ACTION_RESULT
													.equals(typeAttr.getValue()) && StrutsXmlConstants.ACTION_NAME_PARAM
												.equals(nameAttr.getValue()));
							if (correctTypeAndName) {
								proposals = computeResultBodyProposals(
										context.getDocument(),
										context.getInvocationOffset(),
										typeAttr == null ? null : typeAttr
												.getValue());
								sortProposals = true;
							}
						}
					}
				}
			}
		}

		return createAttrCompletionProposals(proposals, elementValuePrefix,
				proposalRegion, multiValueSeparator, elementValue,
				sortProposals);
	}

	private String[][] computeResultBodyProposals(final IDocument document,
			final int offset, final String typeAttrValue) {
		Set<String> set = null;
		// assume that default is dispatcher for now, TODO improve
		// that
		if (typeAttrValue == null
				|| StrutsXmlConstants.DISPATCHER_RESULT.equals(typeAttrValue)) {
			set = findFilesPaths(document, DISPATCHER_EXTENSIONS);
		} else if (StrutsXmlConstants.TILES_RESULT.equals(typeAttrValue)) {
			set = findTilesDefinitionNames(document);
		} else if (StrutsXmlConstants.FREEMARKER_RESULT.equals(typeAttrValue)) {
			set = findFilesPaths(document, FREEMARKER_EXTENSIONS);
		} else if (StrutsXmlConstants.REDIRECT_ACTION_RESULT
				.equals(typeAttrValue)) {
			set = findRedirectActionNames(document, offset);
		}

		String[][] proposals = null;
		if (set != null && !set.isEmpty()) {
			proposals = new String[set.size()][2];
			int indx = 0;
			for (String p : set) {
				proposals[indx++][0] = p;
			}
		}
		return proposals;
	}

	private List<ICompletionProposal> createAttrCompletionProposals(
			String[][] proposalsData, String prefix, IRegion region,
			String valueSeparator, String attrvalue, boolean sort) {
		List<ICompletionProposal> list = new ArrayList<ICompletionProposal>();
		if (proposalsData != null && region != null) {
			int replacementOffset = region.getOffset();
			int replacementLength = region.getLength();

			boolean multivalue = valueSeparator != null
					&& attrvalue.contains(valueSeparator);

			List<String> excludes = new ArrayList<String>();

			if (multivalue) {
				int startSeprIndx = prefix.lastIndexOf(valueSeparator) + 1;

				// spaces between valueSeparator and current value prefix
				// (one,_t|wo -> 1; one,_|two -> 1; one,__t|wo -> 2)
				int spacesCount = 0;

				String currentValue = "";

				// first value in attrvalue
				if (startSeprIndx <= 0) {
					currentValue = attrvalue.substring(0,
							attrvalue.indexOf(valueSeparator));
				} else {
					prefix = prefix.substring(startSeprIndx);
					spacesCount = prefix.length();
					prefix = prefix.trim();
					spacesCount = spacesCount - prefix.length();

					int endSeprIndx = attrvalue.indexOf(valueSeparator,
							startSeprIndx);
					if (endSeprIndx <= 0) {
						// last value in attrvalue
						currentValue = attrvalue.substring(startSeprIndx);
					} else {
						// somewhere in the middle of attrvalue
						currentValue = attrvalue.substring(startSeprIndx,
								endSeprIndx);
					}
				}

				currentValue = currentValue.trim();

				if (spacesCount < 0) {
					spacesCount = 0;
				}

				replacementOffset = replacementOffset + startSeprIndx
						+ spacesCount;
				replacementLength = currentValue.length();

				// exclude already defined values except current value
				String[] valArr = attrvalue.split(valueSeparator);
				for (String val : valArr) {
					if (!currentValue.equalsIgnoreCase(val.trim())) {
						excludes.add(val.trim());
					}
				}
			}

			for (String[] proposal : proposalsData) {
				if (proposal[0].toLowerCase().startsWith(prefix.toLowerCase())
						&& !excludes.contains(proposal[0])) {
					list.add(new CompletionProposal(proposal[0],
							replacementOffset, replacementLength, proposal[0]
									.length(), null, null, null, proposal[1]));
				}
			}
		}

		if (sort) {
			Collections.sort(list, proposalComparator);
		}

		return list;
	}

	private Set<String> findFilesPaths(final IDocument currentDocument,
			final List<String> extensions) {
		final Set<String> paths = new HashSet<String>();
		try {
			IProject project = ProjectUtil.getCurrentProject(currentDocument);
			if (project != null && project.exists()) {
				IVirtualComponent rootComponent = ComponentCore
						.createComponent(project);
				final IVirtualFolder rootFolder = rootComponent.getRootFolder();

				rootFolder.getUnderlyingResource().accept(
						new IResourceVisitor() {
							@Override
							public boolean visit(IResource resource)
									throws CoreException {
								if (resource.isAccessible()
										&& resource.getType() == IResource.FILE
										&& extensions.contains(resource
												.getFileExtension()
												.toLowerCase())) {
									IPath path = resource
											.getProjectRelativePath()
											.makeRelativeTo(
													rootFolder
															.getProjectRelativePath())
											.makeAbsolute();

									paths.add(path.toString());
								}
								return true;
							}
						});
			}
		} catch (CoreException e) {
			e.printStackTrace();
		}

		return paths;
	}

	private Set<String> findTilesDefinitionNames(final IDocument currentDocument) {
		final Set<String> names = new HashSet<String>();
		try {
			final IDocumentProvider provider = new TextFileDocumentProvider();
			IProject project = ProjectUtil.getCurrentProject(currentDocument);
			if (project != null && project.exists()) {
				project.accept(new IResourceVisitor() {
					@Override
					public boolean visit(IResource resource)
							throws CoreException {
						if (resource.isAccessible()
								&& resource.getType() == IResource.FILE
								&& "xml".equalsIgnoreCase(resource
										.getFileExtension())
								&& resource
										.getName()
										.toLowerCase()
										.contains(
												StrutsXmlConstants.TILES_RESULT)) {
							provider.connect(resource);
							IDocument document = provider.getDocument(resource);
							provider.disconnect(resource);

							names.addAll(tilesXmlParser
									.getDefinitionNames(document));
						}
						return true;
					}
				});
			}
		} catch (CoreException e) {
			e.printStackTrace();
		}

		return names;
	}

	private Set<String> findRedirectActionNames(final IDocument document,
			final int offset) {
		Set<String> set = null;
		TagRegion packageTag = strutsXmlParser.getParentTagRegion(document,
				offset, StrutsXmlConstants.PACKAGE_TAG);
		if (packageTag != null) {
			ElementRegion namespaceAttr = packageTag.getAttrs().get(
					StrutsXmlConstants.NAMESPACE_ATTR);
			set = strutsXmlParser.getActionNames(document,
					namespaceAttr == null ? "" : namespaceAttr.getValue());
		}
		return set;
	}

	@Override
	public List<IContextInformation> computeContextInformation(
			CompletionProposalInvocationContext context,
			IProgressMonitor monitor) {
		return null;
	}

	@Override
	public String getErrorMessage() {
		return null;
	}

	@Override
	public void sessionStarted() {
	}

	@Override
	public void sessionEnded() {
	}
}
