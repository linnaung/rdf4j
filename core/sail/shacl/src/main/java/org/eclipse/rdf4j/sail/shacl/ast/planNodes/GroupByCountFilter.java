/*******************************************************************************
 * Copyright (c) 2020 Eclipse RDF4J contributors.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 *******************************************************************************/

package org.eclipse.rdf4j.sail.shacl.ast.planNodes;

import java.util.Objects;
import java.util.function.Function;

import org.apache.commons.text.StringEscapeUtils;
import org.eclipse.rdf4j.common.iteration.CloseableIteration;
import org.eclipse.rdf4j.sail.shacl.wrapper.data.ConnectionsGroup;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Håvard Ottestad
 */
public class GroupByCountFilter implements PlanNode {

	private static final Logger logger = LoggerFactory.getLogger(GroupByCountFilter.class);

	private final Function<Long, Boolean> filter;
	PlanNode parent;
	private boolean printed = false;
	private ValidationExecutionLogger validationExecutionLogger;

	public GroupByCountFilter(PlanNode parent, Function<Long, Boolean> filter, ConnectionsGroup connectionsGroup) {
		this.parent = PlanNodeHelper.handleSorting(this, parent, connectionsGroup);
		this.filter = filter;
	}

	@Override
	public CloseableIteration<? extends ValidationTuple> iterator() {
		return new LoggingCloseableIteration(this, validationExecutionLogger) {

			private CloseableIteration<? extends ValidationTuple> parentIterator;

			ValidationTuple next;
			ValidationTuple tempNext;

			@Override
			protected void init() {
				parentIterator = parent.iterator();
			}

			private void calculateNext() {
				if (next != null) {
					return;
				}

				while (next == null && (tempNext != null || parentIterator.hasNext())) {
					if (tempNext == null) {
						tempNext = parentIterator.next();
					}

					this.next = tempNext;
					long count = 0;

					while (tempNext != null && tempNext.sameTargetAs(this.next)) {

						if (tempNext.hasValue()) {
							count++;
						}

						tempNext = null;

						if (parentIterator.hasNext()) {
							tempNext = parentIterator.next();
						}

					}

					if (!filter.apply(count)) {
						logger.debug(
								"Tuple rejected because its count does not pass the filter. Actual count: {}, Tuple: {}",
								count, this.next);
						this.next = null;
					} else {
						logger.trace("Tuple accepted because its count passes the filter. Actual count: {}, Tuple: {}",
								count, this.next);
					}
				}

			}

			@Override
			public void localClose() {
				if (parentIterator != null) {
					parentIterator.close();
				}
			}

			@Override
			protected boolean localHasNext() {
				calculateNext();

				return next != null;
			}

			@Override
			protected ValidationTuple loggingNext() {

				calculateNext();

				ValidationTuple temp = next;
				next = null;

				return temp;
			}

		};
	}

	@Override
	public int depth() {
		return parent.depth() + 1;
	}

	@Override
	public void getPlanAsGraphvizDot(StringBuilder stringBuilder) {
		if (printed) {
			return;
		}
		printed = true;
		stringBuilder.append(getId() + " [label=\"" + StringEscapeUtils.escapeJava(this.toString()) + "\"];")
				.append("\n");
		stringBuilder.append(parent.getId() + " -> " + getId()).append("\n");
		parent.getPlanAsGraphvizDot(stringBuilder);
	}

	@Override
	public String toString() {
		return "GroupByCountFilter";
	}

	@Override
	public String getId() {
		return System.identityHashCode(this) + "";
	}

	@Override
	public void receiveLogger(ValidationExecutionLogger validationExecutionLogger) {
		this.validationExecutionLogger = validationExecutionLogger;
		parent.receiveLogger(validationExecutionLogger);
	}

	@Override
	public boolean producesSorted() {
		return true;
	}

	@Override
	public boolean requiresSorted() {
		return true;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (o == null || getClass() != o.getClass()) {
			return false;
		}
		GroupByCountFilter that = (GroupByCountFilter) o;
		return filter.equals(that.filter) && parent.equals(that.parent);
	}

	@Override
	public int hashCode() {
		return Objects.hash(filter, parent);
	}
}
