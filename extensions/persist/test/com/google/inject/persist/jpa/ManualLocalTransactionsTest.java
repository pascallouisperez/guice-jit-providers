/**
 * Copyright (C) 2010 Google, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.inject.persist.jpa;

import com.google.inject.Guice;
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.persist.PersistModule;
import com.google.inject.persist.PersistenceService;
import com.google.inject.persist.Transactional;
import com.google.inject.persist.UnitOfWork;
import com.google.inject.persist.WorkManager;
import java.util.Date;
import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import junit.framework.TestCase;

/**
 * For instance, a session-per-request strategy will control the opening and closing of the EM at
 * its own (manual) discretion. As opposed to a transactional unit of work.
 *
 * @author Dhanji R. Prasanna (dhanji@gmail.com)
 */
public class ManualLocalTransactionsTest extends TestCase {
  private Injector injector;
  private static final String UNIQUE_TEXT = "some unique text" + new Date();
  private static final String UNIQUE_TEXT_2 = "some other unique text" + new Date();

  public void setUp() {
    injector = Guice.createInjector(new PersistModule() {

      @Override
      protected void configurePersistence() {
        workAcross(UnitOfWork.REQUEST).usingJpa("testUnit");
      }
    });

    //startup persistence
    injector.getInstance(PersistenceService.class).start();
  }

  public void tearDown() {
    injector.getInstance(EntityManagerFactory.class).close();
  }

  public void testSimpleCrossTxnWork() {
    injector.getInstance(WorkManager.class).begin();

    //pretend that the request was started here
    EntityManager em = injector.getInstance(EntityManager.class);

    JpaTestEntity entity = injector.getInstance(TransactionalObject.class).runOperationInTxn();
    injector.getInstance(TransactionalObject.class).runOperationInTxn2();

    //persisted entity should remain in the same em (which should still be open)
    assertTrue("EntityManager  appears to have been closed across txns!",
        injector.getInstance(EntityManager.class).contains(entity));
    assertTrue("EntityManager  appears to have been closed across txns!", em.contains(entity));
    assertTrue("EntityManager appears to have been closed across txns!", em.isOpen());

    injector.getInstance(WorkManager.class).end();
    injector.getInstance(WorkManager.class).begin();

    //try to query them back out
    em = injector.getInstance(EntityManager.class);
    assertNotNull(em.createQuery("from JpaTestEntity where text = :text")
        .setParameter("text", UNIQUE_TEXT).getSingleResult());
    assertNotNull(em.createQuery("from JpaTestEntity where text = :text")
        .setParameter("text", UNIQUE_TEXT_2).getSingleResult());
    em.close();

    assertFalse(em.isOpen());
  }

  public static class TransactionalObject {
    @Inject EntityManager em;

    @Transactional
    public JpaTestEntity runOperationInTxn() {
      JpaTestEntity entity = new JpaTestEntity();
      entity.setText(UNIQUE_TEXT);
      em.persist(entity);

      return entity;
    }

    @Transactional
    public void runOperationInTxn2() {
      JpaTestEntity entity = new JpaTestEntity();
      entity.setText(UNIQUE_TEXT_2);
      em.persist(entity);
    }

  }
}