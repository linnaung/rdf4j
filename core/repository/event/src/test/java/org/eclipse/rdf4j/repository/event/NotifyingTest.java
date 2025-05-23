/*******************************************************************************
 * Copyright (c) 2015 Eclipse RDF4J contributors, Aduna, and others.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 *******************************************************************************/
package org.eclipse.rdf4j.repository.event;

import static org.eclipse.rdf4j.query.QueryLanguage.SPARQL;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

import org.eclipse.rdf4j.model.IRI;
import org.eclipse.rdf4j.model.Resource;
import org.eclipse.rdf4j.model.Value;
import org.eclipse.rdf4j.model.ValueFactory;
import org.eclipse.rdf4j.model.impl.SimpleValueFactory;
import org.eclipse.rdf4j.query.MalformedQueryException;
import org.eclipse.rdf4j.query.QueryLanguage;
import org.eclipse.rdf4j.query.Update;
import org.eclipse.rdf4j.query.UpdateExecutionException;
import org.eclipse.rdf4j.query.impl.AbstractUpdate;
import org.eclipse.rdf4j.repository.Repository;
import org.eclipse.rdf4j.repository.RepositoryConnection;
import org.eclipse.rdf4j.repository.RepositoryException;
import org.eclipse.rdf4j.repository.base.RepositoryConnectionWrapper;
import org.eclipse.rdf4j.repository.base.RepositoryWrapper;
import org.eclipse.rdf4j.repository.event.base.NotifyingRepositoryConnectionWrapper;
import org.eclipse.rdf4j.repository.event.base.RepositoryConnectionListenerAdapter;
import org.junit.jupiter.api.Test;

/**
 * @author James Leigh
 */
public class NotifyingTest {

	static class InvocationHandlerStub implements InvocationHandler {

		@Override
		public Object invoke(Object proxy, Method method, Object[] args) {
			if (Boolean.TYPE.equals(method.getReturnType())) {
				return false;
			}
			return null;
		}
	}

	static class RepositoryStub extends RepositoryWrapper {

		@Override
		public RepositoryConnection getConnection() throws RepositoryException {
			ClassLoader cl = NotifyingTest.class.getClassLoader();
			Class<?>[] classes = new Class[] { RepositoryConnection.class };
			InvocationHandlerStub handler = new InvocationHandlerStub();
			Object proxy = Proxy.newProxyInstance(cl, classes, handler);
			return (RepositoryConnection) proxy;
		}

		@Override
		public ValueFactory getValueFactory() {
			return SimpleValueFactory.getInstance();
		}
	}

	static class RepositoryConnectionStub extends RepositoryConnectionWrapper {

		@Override
		protected boolean isDelegatingAdd() {
			return false;
		}

		@Override
		protected boolean isDelegatingRead() {
			return false;
		}

		@Override
		protected boolean isDelegatingRemove() {
			return false;
		}

		public RepositoryConnectionStub() throws RepositoryException {
			super(new RepositoryStub());
			setDelegate(getRepository().getConnection());
		}
	}

	static class UpdateStub extends AbstractUpdate {

		@Override
		public void execute() throws UpdateExecutionException {
		}
	}

	@Test
	public void testUpdate() {
		final Update updateStub = new UpdateStub();
		final RepositoryConnection stub = new RepositoryConnectionStub() {

			@Override
			public Update prepareUpdate(QueryLanguage ql, String query, String baseURI)
					throws MalformedQueryException, RepositoryException {
				return updateStub;
			}
		};
		Repository repo = stub.getRepository();
		NotifyingRepositoryConnection con = new NotifyingRepositoryConnectionWrapper(repo, stub);
		con.addRepositoryConnectionListener(new RepositoryConnectionListenerAdapter() {

			@Override
			public void execute(RepositoryConnection conn, QueryLanguage ql, String update, String baseURI,
					Update operation) {
				assertEquals(stub, conn);
				assertEquals(SPARQL, ql);
				assertEquals("DELETE DATA { <> <> <> }", update);
				assertEquals("http://example.com/", baseURI);
				assertEquals(updateStub, operation);
			}
		});
		Update update = con.prepareUpdate(SPARQL, "DELETE DATA { <> <> <> }", "http://example.com/");
		update.execute();
	}

	@Test
	public void testRemove() {
		ValueFactory vf = SimpleValueFactory.getInstance();
		final IRI uri = vf.createIRI("http://example.com/");
		final RepositoryConnection stub = new RepositoryConnectionStub() {

			@Override
			protected void removeWithoutCommit(Resource subject, IRI predicate, Value object, Resource... contexts)
					throws RepositoryException {
			}
		};
		Repository repo = stub.getRepository();
		NotifyingRepositoryConnection con = new NotifyingRepositoryConnectionWrapper(repo, stub);
		con.addRepositoryConnectionListener(new RepositoryConnectionListenerAdapter() {

			@Override
			public void remove(RepositoryConnection conn, Resource subject, IRI predicate, Value object,
					Resource... contexts) {
				assertEquals(stub, conn);
				assertEquals(uri, subject);
				assertEquals(uri, predicate);
				assertEquals(uri, object);
				assertEquals(0, contexts.length);
			}

		});
		con.remove(con.getValueFactory().createStatement(uri, uri, uri));
	}
}
