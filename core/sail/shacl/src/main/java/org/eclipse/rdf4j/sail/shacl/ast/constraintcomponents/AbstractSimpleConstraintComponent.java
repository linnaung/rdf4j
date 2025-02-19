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

package org.eclipse.rdf4j.sail.shacl.ast.constraintcomponents;

import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

import org.eclipse.rdf4j.model.IRI;
import org.eclipse.rdf4j.model.Literal;
import org.eclipse.rdf4j.model.Resource;
import org.eclipse.rdf4j.model.Value;
import org.eclipse.rdf4j.sail.shacl.SourceConstraintComponent;
import org.eclipse.rdf4j.sail.shacl.ValidationSettings;
import org.eclipse.rdf4j.sail.shacl.ast.ShaclUnsupportedException;
import org.eclipse.rdf4j.sail.shacl.ast.SparqlFragment;
import org.eclipse.rdf4j.sail.shacl.ast.StatementMatcher;
import org.eclipse.rdf4j.sail.shacl.ast.StatementMatcher.Variable;
import org.eclipse.rdf4j.sail.shacl.ast.ValidationApproach;
import org.eclipse.rdf4j.sail.shacl.ast.ValidationQuery;
import org.eclipse.rdf4j.sail.shacl.ast.paths.Path;
import org.eclipse.rdf4j.sail.shacl.ast.planNodes.AbstractBulkJoinPlanNode;
import org.eclipse.rdf4j.sail.shacl.ast.planNodes.AllTargetsPlanNode;
import org.eclipse.rdf4j.sail.shacl.ast.planNodes.BufferedPlanNode;
import org.eclipse.rdf4j.sail.shacl.ast.planNodes.BulkedExternalInnerJoin;
import org.eclipse.rdf4j.sail.shacl.ast.planNodes.EmptyNode;
import org.eclipse.rdf4j.sail.shacl.ast.planNodes.FilterPlanNode;
import org.eclipse.rdf4j.sail.shacl.ast.planNodes.InnerJoin;
import org.eclipse.rdf4j.sail.shacl.ast.planNodes.PlanNode;
import org.eclipse.rdf4j.sail.shacl.ast.planNodes.PlanNodeProvider;
import org.eclipse.rdf4j.sail.shacl.ast.planNodes.ShiftToPropertyShape;
import org.eclipse.rdf4j.sail.shacl.ast.planNodes.UnBufferedPlanNode;
import org.eclipse.rdf4j.sail.shacl.ast.planNodes.UnionNode;
import org.eclipse.rdf4j.sail.shacl.ast.planNodes.Unique;
import org.eclipse.rdf4j.sail.shacl.ast.planNodes.ValidationTuple;
import org.eclipse.rdf4j.sail.shacl.ast.targets.EffectiveTarget;
import org.eclipse.rdf4j.sail.shacl.ast.targets.TargetChain;
import org.eclipse.rdf4j.sail.shacl.wrapper.data.ConnectionsGroup;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class AbstractSimpleConstraintComponent extends AbstractConstraintComponent {

	private static final Logger logger = LoggerFactory.getLogger(AbstractSimpleConstraintComponent.class);

	private Resource id;
	TargetChain targetChain;

	public AbstractSimpleConstraintComponent(Resource id) {
		this.id = id;
	}

	public AbstractSimpleConstraintComponent() {
	}

	public Resource getId() {
		return id;
	}

	@Override
	public TargetChain getTargetChain() {
		return targetChain;
	}

	@Override
	public void setTargetChain(TargetChain targetChain) {
		this.targetChain = targetChain;
	}

	@Override
	public PlanNode generateTransactionalValidationPlan(ConnectionsGroup connectionsGroup,
			ValidationSettings validationSettings, PlanNodeProvider overrideTargetNode, Scope scope) {

		boolean negatePlan = false;
		StatementMatcher.StableRandomVariableProvider stableRandomVariableProvider = new StatementMatcher.StableRandomVariableProvider();

		EffectiveTarget effectiveTarget = targetChain.getEffectiveTarget(scope,
				connectionsGroup.getRdfsSubClassOfReasoner(), stableRandomVariableProvider);
		Optional<Path> path = targetChain.getPath();

		if (overrideTargetNode != null) {
			return getPlanNodeForOverrideTargetNode(connectionsGroup, validationSettings, overrideTargetNode, scope,
					negatePlan, stableRandomVariableProvider, effectiveTarget, path);
		} else if (scope == Scope.nodeShape) {
			return effectiveTarget.getPlanNode(connectionsGroup, validationSettings.getDataGraph(), scope, false,
					p -> getFilterAttacherWithNegation(negatePlan, p, connectionsGroup));
		} else {

			PlanNode invalidValuesDirectOnPath = path.get()
					.getAnyAdded(connectionsGroup, validationSettings.getDataGraph(),
							planNode -> getFilterAttacherWithNegation(negatePlan, planNode, connectionsGroup));

			PlanNode addedTargets = effectiveTarget.getPlanNode(connectionsGroup, validationSettings.getDataGraph(),
					scope, false, null);

			InnerJoin innerJoin = new InnerJoin(addedTargets, invalidValuesDirectOnPath, connectionsGroup);

			if (connectionsGroup.getStats().wasEmptyBeforeTransaction()) {
				return innerJoin.getJoined(UnBufferedPlanNode.class);
			} else {
				PlanNode top = innerJoin.getJoined(BufferedPlanNode.class);

				// tuples from invalidValuesDirectOnPath that didn't match a target from addedTargets
				PlanNode discardedRight = innerJoin.getDiscardedRight(BufferedPlanNode.class);

				PlanNode typeFilterPlan = effectiveTarget.getTargetFilter(connectionsGroup,
						validationSettings.getDataGraph(), discardedRight);

				typeFilterPlan = effectiveTarget.extend(typeFilterPlan, connectionsGroup,
						validationSettings.getDataGraph(),
						scope,
						EffectiveTarget.Extend.left, true, null);

				top = UnionNode.getInstance(connectionsGroup, top, typeFilterPlan);

				PlanNode bulkedExternalInnerJoin = new BulkedExternalInnerJoin(
						effectiveTarget.getPlanNode(connectionsGroup, validationSettings.getDataGraph(), scope, false,
								null),
						connectionsGroup.getBaseConnection(),
						validationSettings.getDataGraph(),
						path.get()
								.getTargetQueryFragment(new Variable("a"),
										new Variable("c"),
										connectionsGroup.getRdfsSubClassOfReasoner(),
										stableRandomVariableProvider, Set.of()),
						connectionsGroup.hasPreviousStateConnection(),
						connectionsGroup.getPreviousStateConnection(),
						b -> new ValidationTuple(b.getValue("a"), b.getValue("c"), scope, true,
								validationSettings.getDataGraph()),
						connectionsGroup, AbstractBulkJoinPlanNode.DEFAULT_VARS);

				top = UnionNode.getInstance(connectionsGroup, top, bulkedExternalInnerJoin);

				return getFilterAttacherWithNegation(negatePlan, top, connectionsGroup);

			}
		}
	}

	private PlanNode getPlanNodeForOverrideTargetNode(ConnectionsGroup connectionsGroup,
			ValidationSettings validationSettings, PlanNodeProvider overrideTargetNode, Scope scope, boolean negatePlan,
			StatementMatcher.StableRandomVariableProvider stableRandomVariableProvider, EffectiveTarget effectiveTarget,
			Optional<Path> path) {
		PlanNode planNode;

		if (scope == Scope.nodeShape) {
			PlanNode overrideTargetPlanNode = overrideTargetNode.getPlanNode();

			if (overrideTargetPlanNode instanceof AllTargetsPlanNode) {
				PlanNode allTargets = effectiveTarget.getAllTargets(connectionsGroup,
						validationSettings.getDataGraph(), scope);
				allTargets = getFilterAttacherWithNegation(negatePlan, allTargets, connectionsGroup);

				if (effectiveTarget.size() > 1) {
					allTargets = Unique.getInstance(allTargets, true, connectionsGroup);
				}
				return allTargets;
			} else {
				PlanNode extend = effectiveTarget.extend(overrideTargetPlanNode, connectionsGroup,
						validationSettings.getDataGraph(), scope,
						EffectiveTarget.Extend.right,
						false,
						p -> getFilterAttacherWithNegation(negatePlan, p, connectionsGroup)
				);

				if (effectiveTarget.size() > 1) {
					extend = Unique.getInstance(extend, true, connectionsGroup);
				}

				return extend;

			}

		} else {
			PlanNode overrideTargetPlanNode = overrideTargetNode.getPlanNode();

			if (overrideTargetPlanNode instanceof AllTargetsPlanNode) {
				// We are cheating a bit here by retrieving all the targets and values at the same time by
				// pretending to be in node shape scope and then shifting the results back to property shape scope
				PlanNode allTargets = targetChain
						.getEffectiveTarget(Scope.nodeShape,
								connectionsGroup.getRdfsSubClassOfReasoner(), stableRandomVariableProvider)
						.getAllTargets(connectionsGroup, validationSettings.getDataGraph(), Scope.nodeShape);
				allTargets = new ShiftToPropertyShape(allTargets, connectionsGroup);

				allTargets = getFilterAttacherWithNegation(negatePlan, allTargets, connectionsGroup);

				if (effectiveTarget.size() > 1) {
					allTargets = Unique.getInstance(allTargets, true, connectionsGroup);
				}

				return allTargets;

			} else {

				overrideTargetPlanNode = effectiveTarget.extend(overrideTargetPlanNode, connectionsGroup,
						validationSettings.getDataGraph(), scope,
						EffectiveTarget.Extend.right, false, null);

				if (effectiveTarget.size() > 1) {
					overrideTargetPlanNode = Unique.getInstance(overrideTargetPlanNode, true, connectionsGroup);
				}

				planNode = new BulkedExternalInnerJoin(overrideTargetPlanNode,
						connectionsGroup.getBaseConnection(),
						validationSettings.getDataGraph(), path.get()
								.getTargetQueryFragment(new Variable("a"),
										new Variable("c"),
										connectionsGroup.getRdfsSubClassOfReasoner(), stableRandomVariableProvider,
										Set.of()),
						false, null,
						BulkedExternalInnerJoin.getMapper("a", "c", scope, validationSettings.getDataGraph()),
						connectionsGroup, AbstractBulkJoinPlanNode.DEFAULT_VARS);
				planNode = connectionsGroup.getCachedNodeFor(planNode);
			}
		}

		return getFilterAttacherWithNegation(negatePlan, planNode, connectionsGroup);
	}

	@Override
	public ValidationQuery generateSparqlValidationQuery(ConnectionsGroup connectionsGroup,
			ValidationSettings validationSettings, boolean negatePlan, boolean negateChildren, Scope scope) {

		StatementMatcher.StableRandomVariableProvider stableRandomVariableProvider = new StatementMatcher.StableRandomVariableProvider();

		EffectiveTarget effectiveTarget = targetChain.getEffectiveTarget(scope,
				connectionsGroup.getRdfsSubClassOfReasoner(), stableRandomVariableProvider);
		String query = effectiveTarget.getQuery(false);

		Variable<Value> value;

		if (scope == Scope.nodeShape) {

			value = null;

			query += "\n" + getSparqlFilter(negatePlan, effectiveTarget.getTargetVar(), stableRandomVariableProvider);

		} else {
			value = Variable.VALUE;

			Optional<SparqlFragment> sparqlFragment = targetChain.getPath()
					.map(p -> p.getTargetQueryFragment(effectiveTarget.getTargetVar(), value,
							connectionsGroup.getRdfsSubClassOfReasoner(), stableRandomVariableProvider, Set.of()));

			String pathQuery = sparqlFragment.map(SparqlFragment::getFragment).orElseThrow(IllegalStateException::new);

			query += "\n" + pathQuery;
			query += "\n" + getSparqlFilter(negatePlan, value, stableRandomVariableProvider);
		}

		var allTargetVariables = effectiveTarget.getAllTargetVariables();

		return new ValidationQuery(getTargetChain().getNamespaces(), query, allTargetVariables, value, scope, this,
				null, null);

	}

	private String getSparqlFilter(boolean negatePlan, Variable<Value> variable,
			StatementMatcher.StableRandomVariableProvider stableRandomVariableProvider) {
		// We use BIND and COALESCE because the filter expression could cause an error and the SHACL spec implicitly
		// says that values that cause errors are in violation of the constraint.

		assert !negatePlan : "This code has not been tested with negated plans! Should be still coalesce to true?";

		String tempVar = stableRandomVariableProvider.next().asSparqlVariable();

		return String.join("\n",
				"BIND((" + getSparqlFilterExpression(variable, negatePlan) + ") as " + tempVar + ")",
				"FILTER(COALESCE(" + tempVar + ", true))"
		);
	}

	/**
	 * Simple constraints need only implement this method to support SPARQL based validation. The returned filter body
	 * should evaluate to true for values that fail validation, unless negated==true. If the filter condition throws an
	 * error (a SPARQL runtime error, not Java error) then the error will be caught and coalesced to `true`.
	 *
	 * @param variable
	 * @param negated
	 * @return a string that is the body of a SPARQL filter
	 */
	abstract String getSparqlFilterExpression(Variable<Value> variable, boolean negated);

	private PlanNode getFilterAttacherWithNegation(boolean negatePlan, PlanNode allTargets,
			ConnectionsGroup connectionsGroup) {
		if (negatePlan) {
			allTargets = getFilterAttacher(connectionsGroup).apply(allTargets).getTrueNode(UnBufferedPlanNode.class);
		} else {
			allTargets = getFilterAttacher(connectionsGroup).apply(allTargets).getFalseNode(UnBufferedPlanNode.class);
		}
		return allTargets;
	}

	@Override
	public ValidationApproach getPreferredValidationApproach(ConnectionsGroup connectionsGroup) {
		return ValidationApproach.Transactional;
	}

	@Override
	public ValidationApproach getOptimalBulkValidationApproach() {
		return ValidationApproach.SPARQL;
	}

	@Override
	public SourceConstraintComponent getConstraintComponent() {
		throw new ShaclUnsupportedException(this.getClass().getSimpleName());
	}

	abstract Function<PlanNode, FilterPlanNode> getFilterAttacher(ConnectionsGroup connectionsGroup);

	String literalToString(Literal literal) {
		IRI datatype = (literal).getDatatype();
		if (datatype == null) {
			return "\"" + literal.stringValue() + "\"";
		}
		if ((literal).getLanguage().isPresent()) {
			return "\"" + literal.stringValue() + "\"@" + (literal).getLanguage().get();
		}
		return "\"" + literal.stringValue() + "\"^^<" + datatype.stringValue() + ">";

	}

	@Override
	public PlanNode getAllTargetsPlan(ConnectionsGroup connectionsGroup, Resource[] dataGraph, Scope scope,
			StatementMatcher.StableRandomVariableProvider stableRandomVariableProvider,
			ValidationSettings validationSettings) {

		if (scope == Scope.propertyShape) {

			EffectiveTarget effectiveTarget = getTargetChain()
					.getEffectiveTarget(
							Scope.nodeShape,
							connectionsGroup.getRdfsSubClassOfReasoner(),
							stableRandomVariableProvider
					);

			PlanNode allTargetsPlan = effectiveTarget
					.getPlanNode(
							connectionsGroup,
							dataGraph, Scope.nodeShape,
							true,
							null
					);

			return Unique.getInstance(new ShiftToPropertyShape(allTargetsPlan, connectionsGroup),
					effectiveTarget.size() > 1, connectionsGroup);
		}
		return EmptyNode.getInstance();
	}

}
