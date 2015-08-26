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

import java.util.HashMap;
import java.util.Map;

import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.IDocumentPartitioner;
import org.eclipse.jface.text.ITypedRegion;
import org.eclipse.jface.text.rules.FastPartitioner;
import org.eclipse.jface.text.rules.IPredicateRule;
import org.eclipse.jface.text.rules.MultiLineRule;
import org.eclipse.jface.text.rules.RuleBasedPartitionScanner;
import org.eclipse.jface.text.rules.SingleLineRule;
import org.eclipse.jface.text.rules.Token;

public class StrutsXmlParser {
	private static final String CLOSE_TAG_TOKEN = "close_tag_token";
	private static final String DOUBLE_QUOTES_TOKEN = "double_quotes_token";
	private static final String SINGLE_QUOTES_TOKEN = "single_quotes_token";

	private static final String[] TAGS = { StrutsXmlConstants.PACKAGE_TAG,
			StrutsXmlConstants.ACTION_TAG, StrutsXmlConstants.RESULT_TAG,
			CLOSE_TAG_TOKEN };

	private static final String[] ATTRS = { StrutsXmlConstants.EXTENDS_ATTR,
			StrutsXmlConstants.NAME_ATTR, StrutsXmlConstants.TYPE_ATTR,
			StrutsXmlConstants.METHOD_ATTR, StrutsXmlConstants.CLASS_ATTR,
			DOUBLE_QUOTES_TOKEN, SINGLE_QUOTES_TOKEN };

	private StrutsXmlParser() {
	}

	public static TagRegion getTagRegion(final IDocument document,
			final int offset) {
		IDocumentPartitioner partitioner = null;
		try {
			TagRegion result = null;

			IPredicateRule[] tagRules = new IPredicateRule[TAGS.length];
			for (int i = 0; i < TAGS.length; i++) {
				if (CLOSE_TAG_TOKEN.equals(TAGS[i])) {
					tagRules[i] = new MultiLineRule("</", ">", new Token(
							TAGS[i]));
				} else {
					tagRules[i] = new MultiLineRule("<" + TAGS[i], ">",
							new Token(TAGS[i]));
				}
			}

			RuleBasedPartitionScanner scanner = new RuleBasedPartitionScanner();
			scanner.setPredicateRules(tagRules);

			partitioner = new FastPartitioner(scanner, TAGS);

			partitioner.connect(document);

			ITypedRegion tagRegion = partitioner.getPartition(offset);

			ElementRegion currentElement = null;
			String elementValuePrefix = null;

			if (IDocument.DEFAULT_CONTENT_TYPE.equals(tagRegion.getType())) {
				ITypedRegion nextTagRegion = partitioner.getPartition(tagRegion
						.getOffset() + tagRegion.getLength());
				if (CLOSE_TAG_TOKEN.equals(nextTagRegion.getType())) {
					try {
						currentElement = new ElementRegion(null, document.get(
								tagRegion.getOffset(), tagRegion.getLength()),
								tagRegion.getOffset());

						elementValuePrefix = document.get(
								tagRegion.getOffset(),
								offset - tagRegion.getOffset());

						// get start tag of current tag body
						tagRegion = partitioner.getPartition(tagRegion
								.getOffset() - 1);
					} catch (BadLocationException e) {
						e.printStackTrace();
					}
				}
			}

			if (!IDocument.DEFAULT_CONTENT_TYPE.equals(tagRegion.getType())) {
				IPredicateRule[] attrRules = new IPredicateRule[ATTRS.length];
				for (int i = 0; i < ATTRS.length; i++) {
					if (DOUBLE_QUOTES_TOKEN.equals(ATTRS[i])) {
						attrRules[i] = new SingleLineRule("\"", "\"",
								new Token(ATTRS[i]));
					} else if (SINGLE_QUOTES_TOKEN.equals(ATTRS[i])) {
						attrRules[i] = new SingleLineRule("'", "'", new Token(
								ATTRS[i]));
					} else {
						attrRules[i] = new MultiLineRule(ATTRS[i], "=",
								new Token(ATTRS[i]));
					}
				}

				scanner.setPredicateRules(attrRules);

				partitioner = new FastPartitioner(scanner, ATTRS);

				partitioner.connect(document);

				// all attributes
				Map<String, ElementRegion> allAttrs = new HashMap<String, ElementRegion>();
				ITypedRegion[] regions = partitioner.computePartitioning(
						tagRegion.getOffset(), tagRegion.getLength());
				if (regions != null) {
					String attrKey = null;
					for (ITypedRegion r : regions) {
						// only legal types
						if (!IDocument.DEFAULT_CONTENT_TYPE.equals(r.getType())) {
							if (attrKey != null
									&& (DOUBLE_QUOTES_TOKEN.equals(r.getType()) || SINGLE_QUOTES_TOKEN
											.equals(r.getType()))) {
								try {
									final int valDocOffset = r.getOffset() + 1;
									// get value w/o quotes
									String val = document.get(valDocOffset,
											r.getLength() - 2);

									allAttrs.put(attrKey, new ElementRegion(
											attrKey, val, valDocOffset));

									// if not in tag body and current attribute
									if (currentElement == null
											&& valDocOffset <= offset
											&& r.getOffset() + r.getLength() > offset) {
										currentElement = new ElementRegion(
												attrKey, val, valDocOffset);

										// attribute value to invocation offset
										elementValuePrefix = document.get(
												valDocOffset, offset
														- valDocOffset);
									}
								} catch (BadLocationException e) {
									e.printStackTrace();
								}
								// set key to null
								attrKey = null;
							} else {
								attrKey = r.getType();
							}
						}
					}
				}
				result = new TagRegion(tagRegion.getType(), currentElement,
						elementValuePrefix, allAttrs);
			}

			return result;
		} finally {
			if (partitioner != null) {
				partitioner.disconnect();
			}
		}
	}
}
