/**
 * Copyright 2009 the original author or authors
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */
package org.springframework.extensions.jcr;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.jcr.Item;
import javax.jcr.Node;
import javax.jcr.NodeIterator;
import javax.jcr.Property;
import javax.jcr.PropertyIterator;
import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.jcr.Value;
import javax.jcr.ValueFactory;
import javax.jcr.query.Query;
import javax.jcr.query.QueryManager;
import javax.jcr.query.QueryResult;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.CollectionFactory;
import org.springframework.dao.DataAccessException;
import org.xml.sax.ContentHandler;

/**
 * Helper class that simplifies JCR data access code. Typically used to implement data access or business
 * logic services that use JCR within their implementation but are JCR-agnostic in their interface. Requires a
 * {@link JcrSessionFactory} to provide access to a JCR repository. A workspace name is optional, as the
 * repository will choose the default workspace if a name is not provided.
 * @author Costin Leau
 * @author Sergio Bossa
 * @author Salvatore Incandela
 */
public class JcrTemplate extends JcrAccessor implements JcrOperations {

    private static final Logger LOG = LoggerFactory.getLogger(JcrTemplate.class);

    private boolean allowCreate = false;

    private boolean exposeNativeSession = false;

    /**
	 */
    public JcrTemplate() {
    }

    /**
	 */
    public JcrTemplate(SessionFactory sessionFactory) {
        setSessionFactory(sessionFactory);
        afterPropertiesSet();
    }

    /**
     * @see org.springframework.extensions.jcr.JcrOperations#execute(org.springframework.extensions.jcr.JcrCallback,
     *      boolean)
     */
    public Object execute(JcrCallback action, boolean exposeNativeSession) throws DataAccessException {
        Session session = getSession();
        boolean existingTransaction = SessionFactoryUtils.isSessionThreadBound(session, getSessionFactory());
        if (existingTransaction) {
            LOG.debug("Found thread-bound Session for JcrTemplate");
        }

        try {
            Session sessionToExpose = (exposeNativeSession ? session : createSessionProxy(session));
            Object result = action.doInJcr(sessionToExpose);
            // TODO: does flushing (session.refresh) should work here?
            // flushIfNecessary(session, existingTransaction);
            return result;
        } catch (RepositoryException ex) {
            throw convertJcrAccessException(ex);
            // IOException are not converted here
        } catch (IOException ex) {
            // use method to decouple the static call
            throw convertJcrAccessException(ex);
        } catch (RuntimeException ex) {
            // Callback code threw application exception...
            throw convertJcrAccessException(ex);
        } finally {
            if (existingTransaction) {
                LOG.debug("Not closing pre-bound Jcr Session after JcrTemplate");
            } else {
                SessionFactoryUtils.releaseSession(session, getSessionFactory());
            }
        }
    }

    /**
     * @see org.springframework.extensions.jcr.JcrOperations#execute(org.springframework.extensions.jcr.JcrCallback)
     */
    public Object execute(JcrCallback callback) throws DataAccessException {
        return execute(callback, isExposeNativeSession());
    }

    /**
     * Return a Session for use by this template. A pre-bound Session in case of "allowCreate" turned off and
     * a pre-bound or new Session else (new only if no transactional or otherwise pre-bound Session exists).
     * @see SessionFactoryUtils#getSession
     * @see SessionFactoryUtils#getNewSession
     * @see #setAllowCreate
     */
    protected Session getSession() {
        return SessionFactoryUtils.getSession(getSessionFactory(), allowCreate);
    }

    // -------------------------------------------------------------------------
    // Convenience methods for loading individual objects
    // -------------------------------------------------------------------------

    /**
     * @see org.springframework.extensions.jcr.JcrOperations#addLockToken(java.lang.String)
     */
    public void addLockToken(final String lock) {
        execute(new JcrCallback() {
            /**
             * @see org.springframework.extensions.jcr.JcrCallback#doInJcr(javax.jcr.Session)
             */
            public Object doInJcr(Session session) throws RepositoryException {
                session.addLockToken(lock);
                return null;
            }
        }, true);
    }

    /**
     * @see org.springframework.extensions.jcr.JcrOperations#getAttribute(java.lang.String)
     */
    public Object getAttribute(final String name) {
        return execute(new JcrCallback() {
            /**
             * @see org.springframework.extensions.jcr.JcrCallback#doInJcr(javax.jcr.Session)
             */
            public Object doInJcr(Session session) throws RepositoryException {
                return session.getAttribute(name);
            }
        }, true);
    }

    /**
     * @see org.springframework.extensions.jcr.JcrOperations#getAttributeNames()
     */
    public String[] getAttributeNames() {
        return (String[]) execute(new JcrCallback() {
            /**
             * @see org.springframework.extensions.jcr.JcrCallback#doInJcr(javax.jcr.Session)
             */
            public Object doInJcr(Session session) throws RepositoryException {
                return session.getAttributeNames();
            }
        }, true);
    }

    /**
     * @see org.springframework.extensions.jcr.JcrOperations#getImportContentHandler(java.lang.String, int)
     */
    public ContentHandler getImportContentHandler(final String parentAbsPath, final int uuidBehavior) {
        return (ContentHandler) execute(new JcrCallback() {
            /**
             * @see org.springframework.extensions.jcr.JcrCallback#doInJcr(javax.jcr.Session)
             */
            public Object doInJcr(Session session) throws RepositoryException {
                return session.getImportContentHandler(parentAbsPath, uuidBehavior);
            }
        }, true);
    }

    /**
     * @see org.springframework.extensions.jcr.JcrOperations#getItem(java.lang.String)
     */
    public Item getItem(final String absPath) {
        return (Item) execute(new JcrCallback() {
            /**
             * @see org.springframework.extensions.jcr.JcrCallback#doInJcr(javax.jcr.Session)
             */
            public Object doInJcr(Session session) throws RepositoryException {
                return session.getItem(absPath);
            }
        }, true);
    }

    /**
     * @see org.springframework.extensions.jcr.JcrOperations#getLockTokens()
     */
    public String[] getLockTokens() {
        return (String[]) execute(new JcrCallback() {
            /**
             * @see org.springframework.extensions.jcr.JcrCallback#doInJcr(javax.jcr.Session)
             */
            public Object doInJcr(Session session) throws RepositoryException {
                return session.getLockTokens();
            }
        }, true);
    }

    /**
     * @see org.springframework.extensions.jcr.JcrOperations#getNamespacePrefix(java.lang.String)
     */
    public String getNamespacePrefix(final String uri) {
        return (String) execute(new JcrCallback() {
            /**
             * @see org.springframework.extensions.jcr.JcrCallback#doInJcr(javax.jcr.Session)
             */
            public Object doInJcr(Session session) throws RepositoryException {
                return session.getNamespacePrefix(uri);
            }
        }, true);
    }

    /**
     * @see org.springframework.extensions.jcr.JcrOperations#getNamespacePrefixes()
     */
    public String[] getNamespacePrefixes() {
        return (String[]) execute(new JcrCallback() {
            /**
             * @see org.springframework.extensions.jcr.JcrCallback#doInJcr(javax.jcr.Session)
             */
            public Object doInJcr(Session session) throws RepositoryException {
                return session.getNamespacePrefixes();
            }
        }, true);
    }

    /**
     * @see org.springframework.extensions.jcr.JcrOperations#getNamespaceURI(java.lang.String)
     */
    public String getNamespaceURI(final String prefix) {
        return (String) execute(new JcrCallback() {
            /**
             * @see org.springframework.extensions.jcr.JcrCallback#doInJcr(javax.jcr.Session)
             */
            public Object doInJcr(Session session) throws RepositoryException {
                return session.getNamespaceURI(prefix);
            }
        }, true);
    }

    /**
     * @see org.springframework.extensions.jcr.JcrOperations#getNodeByUUID(java.lang.String)
     */
    public Node getNodeByUUID(final String uuid) {
        return (Node) execute(new JcrCallback() {
            /**
             * @see org.springframework.extensions.jcr.JcrCallback#doInJcr(javax.jcr.Session)
             */
            public Object doInJcr(Session session) throws RepositoryException {
                return session.getNodeByUUID(uuid);
            }
        }, true);
    }

    /**
     * @see org.springframework.extensions.jcr.JcrOperations#getRootNode()
     */
    public Node getRootNode() {
        return (Node) execute(new JcrCallback() {
            /**
             * @see org.springframework.extensions.jcr.JcrCallback#doInJcr(javax.jcr.Session)
             */
            public Object doInJcr(Session session) throws RepositoryException {
                return session.getRootNode();
            }
        }, true);
    }

    /**
     * @see org.springframework.extensions.jcr.JcrOperations#getUserID()
     */
    public String getUserID() {
        return (String) execute(new JcrCallback() {
            /**
             * @see org.springframework.extensions.jcr.JcrCallback#doInJcr(javax.jcr.Session)
             */
            public Object doInJcr(Session session) throws RepositoryException {
                return session.getUserID();
            }
        }, true);
    }

    /**
     * @see org.springframework.extensions.jcr.JcrOperations#getValueFactory()
     */
    public ValueFactory getValueFactory() {
        return (ValueFactory) execute(new JcrCallback() {
            /**
             * @see org.springframework.extensions.jcr.JcrCallback#doInJcr(javax.jcr.Session)
             */
            public Object doInJcr(Session session) throws RepositoryException {
                return session.getValueFactory();
            }
        }, true);
    }

    /**
     * @see org.springframework.extensions.jcr.JcrOperations#hasPendingChanges()
     */
    public boolean hasPendingChanges() {
        return ((Boolean) execute(new JcrCallback() {
            /**
             * @see org.springframework.extensions.jcr.JcrCallback#doInJcr(javax.jcr.Session)
             */
            public Object doInJcr(Session session) throws RepositoryException {
                return new Boolean(session.hasPendingChanges());
            }
        }, true)).booleanValue();
    }

    /**
     * @see org.springframework.extensions.jcr.JcrOperations#importXML(java.lang.String, java.io.InputStream,
     *      int)
     */
    public void importXML(final String parentAbsPath, final InputStream in, final int uuidBehavior) {
        execute(new JcrCallback() {
            /**
             * @see org.springframework.extensions.jcr.JcrCallback#doInJcr(javax.jcr.Session)
             */
            public Object doInJcr(Session session) throws RepositoryException {
                try {
                    session.importXML(parentAbsPath, in, uuidBehavior);
                } catch (IOException e) {
                    throw new JcrSystemException(e);
                }
                return null;
            }
        }, true);
    }

    /**
     * @see org.springframework.extensions.jcr.JcrOperations#refresh(boolean)
     */
    public void refresh(final boolean keepChanges) {
        execute(new JcrCallback() {
            /**
             * @see org.springframework.extensions.jcr.JcrCallback#doInJcr(javax.jcr.Session)
             */
            public Object doInJcr(Session session) throws RepositoryException {
                session.refresh(keepChanges);
                return null;
            }
        }, true);
    }

    /**
     * @see org.springframework.extensions.jcr.JcrOperations#removeLockToken(java.lang.String)
     */
    public void removeLockToken(final String lt) {
        execute(new JcrCallback() {
            /**
             * @see org.springframework.extensions.jcr.JcrCallback#doInJcr(javax.jcr.Session)
             */
            public Object doInJcr(Session session) throws RepositoryException {
                session.removeLockToken(lt);
                return null;
            }
        }, true);
    }

    /**
     * @see org.springframework.extensions.jcr.JcrOperations#rename(javax.jcr.Node, java.lang.String)
     */
    public void rename(final Node node, final String newName) {
        execute(new JcrCallback() {
            /**
             * @see org.springframework.extensions.jcr.JcrCallback#doInJcr(javax.jcr.Session)
             */
            public Object doInJcr(Session session) throws RepositoryException {
                session.move(node.getPath(), node.getParent().getPath() + "/" + newName);
                return null;
            }
        }, true);
    }

    /**
     * @see org.springframework.extensions.jcr.JcrOperations#setNamespacePrefix(java.lang.String,
     *      java.lang.String)
     */
    public void setNamespacePrefix(final String prefix, final String uri) {
        execute(new JcrCallback() {
            /**
             * @see org.springframework.extensions.jcr.JcrCallback#doInJcr(javax.jcr.Session)
             */
            public Object doInJcr(Session session) throws RepositoryException {
                session.setNamespacePrefix(prefix, uri);
                return null;
            }
        }, true);
    }

    /**
     * @see org.springframework.extensions.jcr.JcrOperations#isLive()
     */
    public boolean isLive() {
        return ((Boolean) execute(new JcrCallback() {
            /**
             * @see org.springframework.extensions.jcr.JcrCallback#doInJcr(javax.jcr.Session)
             */
            public Object doInJcr(Session session) throws RepositoryException {
                return new Boolean(session.isLive());
            }
        }, true)).booleanValue();
    }

    /**
     * @see org.springframework.extensions.jcr.JcrOperations#itemExists(java.lang.String)
     */
    public boolean itemExists(final String absPath) {
        return ((Boolean) execute(new JcrCallback() {
            /**
             * @see org.springframework.extensions.jcr.JcrCallback#doInJcr(javax.jcr.Session)
             */
            public Object doInJcr(Session session) throws RepositoryException {
                return new Boolean(session.itemExists(absPath));
            }
        }, true)).booleanValue();
    }

    /**
     * @see org.springframework.extensions.jcr.JcrOperations#move(java.lang.String, java.lang.String)
     */
    public void move(final String srcAbsPath, final String destAbsPath) {
        execute(new JcrCallback() {
            /**
             * @see org.springframework.extensions.jcr.JcrCallback#doInJcr(javax.jcr.Session)
             */
            public Object doInJcr(Session session) throws RepositoryException {
                session.move(srcAbsPath, destAbsPath);
                return null;
            }
        }, true);
    }

    /**
     * @see org.springframework.extensions.jcr.JcrOperations#save()
     */
    public void save() {
        execute(new JcrCallback() {
            /**
             * @see org.springframework.extensions.jcr.JcrCallback#doInJcr(javax.jcr.Session)
             */
            public Object doInJcr(Session session) throws RepositoryException {
                session.save();
                return null;
            }
        }, true);
    }

    /**
     * @see org.springframework.extensions.jcr.JcrOperations#dump(javax.jcr.Node)
     */
    public String dump(final Node node) {

        return (String) execute(new JcrCallback() {
            /**
             * @see org.springframework.extensions.jcr.JcrCallback#doInJcr(javax.jcr.Session)
             */
            public Object doInJcr(Session session) throws RepositoryException {
                Node nd = node;

                if (nd == null)
                    nd = session.getRootNode();

                return dumpNode(nd);
            }

        }, true);

    }

    /**
     * Recursive method for dumping a node. This method is separate to avoid the overhead of searching and
     * opening/closing JCR sessions.
     * @param node
     * @return
     * @throws RepositoryException
     */
    protected String dumpNode(Node node) throws RepositoryException {
        StringBuffer buffer = new StringBuffer();
        buffer.append(node.getPath());

        PropertyIterator properties = node.getProperties();
        while (properties.hasNext()) {
            Property property = properties.nextProperty();
            buffer.append(property.getPath() + "=");
            if (property.getDefinition().isMultiple()) {
                Value[] values = property.getValues();
                for (int i = 0; i < values.length; i++) {
                    if (i > 0) {
                        buffer.append(",");
                    }
                    buffer.append(values[i].getString());
                }
            } else {
                buffer.append(property.getString());
            }
            buffer.append("\n");
        }

        NodeIterator nodes = node.getNodes();
        while (nodes.hasNext()) {
            Node child = nodes.nextNode();
            buffer.append(dumpNode(child));
        }
        return buffer.toString();

    }

    /**
     * @see org.springframework.extensions.jcr.JcrOperations#query(javax.jcr.Node)
     */
    public QueryResult query(final Node node) {

        if (node == null)
            throw new IllegalArgumentException("node can't be null");

        return (QueryResult) execute(new JcrCallback() {
            /**
             * @see org.springframework.extensions.jcr.JcrCallback#doInJcr(javax.jcr.Session)
             */
            public Object doInJcr(Session session) throws RepositoryException {
                boolean debug = LOG.isDebugEnabled();

                // get query manager
                QueryManager manager = session.getWorkspace().getQueryManager();
                if (debug)
                    LOG.debug("retrieved manager " + manager);

                Query query = manager.getQuery(node);
                if (debug)
                    LOG.debug("created query " + query);

                return query.execute();
            }
        }, true);
    }

    /**
     * @see org.springframework.extensions.jcr.JcrOperations#query(java.lang.String)
     */
    public QueryResult query(final String statement) {
        return query(statement, null);
    }

    /**
     * @see org.springframework.extensions.jcr.JcrOperations#query(java.lang.String, java.lang.String)
     */
    public QueryResult query(final String statement, final String language) {

        if (statement == null)
            throw new IllegalArgumentException("statement can't be null");

        return (QueryResult) execute(new JcrCallback() {
            /**
             * @see org.springframework.extensions.jcr.JcrCallback#doInJcr(javax.jcr.Session)
             */
            public Object doInJcr(Session session) throws RepositoryException {
                // check language
                String lang = language;
                if (lang == null)
                    lang = Query.XPATH;
                boolean debug = LOG.isDebugEnabled();

                // get query manager
                QueryManager manager = session.getWorkspace().getQueryManager();
                if (debug)
                    LOG.debug("retrieved manager " + manager);

                Query query = manager.createQuery(statement, lang);
                if (debug)
                    LOG.debug("created query " + query);

                return query.execute();
            }
        }, true);
    }

    /**
     * @see org.springframework.extensions.jcr.JcrOperations#query(java.util.List)
     */
    public Map query(final List list) {
        return query(list, null, false);
    }

    /**
     * @see org.springframework.extensions.jcr.JcrOperations#query(java.util.List, java.lang.String, boolean)
     */
    public Map query(final List list, final String language, final boolean ignoreErrors) {
        if (list == null)
            throw new IllegalArgumentException("list can't be null");

        return (Map) execute(new JcrCallback() {
            /**
             * @see org.springframework.extensions.jcr.JcrCallback#doInJcr(javax.jcr.Session)
             */
            public Object doInJcr(Session session) throws RepositoryException {
                // check language
                String lang = language;
                if (lang == null)
                    lang = Query.XPATH;
                boolean debug = LOG.isDebugEnabled();

                Map<String,QueryResult> map = new LinkedHashMap<String, QueryResult>(list.size());

                // get query manager
                QueryManager manager = session.getWorkspace().getQueryManager();
                if (debug)
                    LOG.debug("retrieved manager " + manager);
                for (Iterator<String> iter = list.iterator(); iter.hasNext();) {
                    String statement = (String) iter.next();

                    Query query = manager.createQuery(statement, lang);
                    if (debug)
                        LOG.debug("created query " + query);

                    QueryResult result;
                    try {
                        result = query.execute();
                        map.put(statement, result);
                    } catch (RepositoryException e) {
                        if (ignoreErrors)
                            map.put(statement, null);
                        else
                            throw convertJcrAccessException(e);
                    }
                }
                return map;
            }
        }, true);
    }

    /**
     * @return Returns the allowCreate.
     */
    public boolean isAllowCreate() {
        return allowCreate;
    }

    /**
     * @param allowCreate The allowCreate to set.
     */
    public void setAllowCreate(boolean allowCreate) {
        this.allowCreate = allowCreate;
    }

    /**
     * Create a close-suppressing proxy for the given Jcr Session.
     * @param session the Jcr Session to create a proxy for
     * @return the Session proxy
     * @see javax.jcr.Session#logout()
     */
    protected Session createSessionProxy(Session session) {
        return (Session) Proxy.newProxyInstance(getClass().getClassLoader(), new Class[] { Session.class }, new LogoutSuppressingInvocationHandler(session));
    }

    /**
     * Invocation handler that suppresses logout calls on JCR Session.
     * @see javax.jcr.Sesion#logout
     */
    private class LogoutSuppressingInvocationHandler implements InvocationHandler {

        private final Session target;

        public LogoutSuppressingInvocationHandler(Session target) {
            this.target = target;
        }

        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            // Invocation on Session interface (or vendor-specific
            // extension) coming in...

            if (method.getName().equals("equals")) {
                // Only consider equal when proxies are identical.
                return (proxy == args[0] ? Boolean.TRUE : Boolean.FALSE);
            } else if (method.getName().equals("hashCode")) {
                // Use hashCode of session proxy.
                return new Integer(hashCode());
            } else if (method.getName().equals("logout")) {
                // Handle close method: suppress, not valid.
                return null;
            }

            // Invoke method on target Session.
            try {
                Object retVal = method.invoke(this.target, args);

                // TODO: watch out for Query returned
                /*
                 * if (retVal instanceof Query) { prepareQuery(((Query) retVal)); }
                 */
                return retVal;
            } catch (InvocationTargetException ex) {
                throw ex.getTargetException();
            }
        }
    }

    protected boolean isVersionable(Node node) throws RepositoryException {
        return node.isNodeType("mix:versionable");
    }

    /**
     * @return Returns the exposeNativeSession.
     */
    public boolean isExposeNativeSession() {
        return exposeNativeSession;
    }

    /**
     * @param exposeNativeSession The exposeNativeSession to set.
     */
    public void setExposeNativeSession(boolean exposeNativeSession) {
        this.exposeNativeSession = exposeNativeSession;
    }

}
