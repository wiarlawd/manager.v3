// Copyright 2006-2009 Google Inc.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.google.enterprise.connector.manager;

import com.google.enterprise.connector.common.I18NUtil;
import com.google.enterprise.connector.instantiator.Instantiator;
import com.google.enterprise.connector.instantiator.InstantiatorException;
import com.google.enterprise.connector.persist.ConnectorNotFoundException;
import com.google.enterprise.connector.persist.ConnectorScheduleStore;
import com.google.enterprise.connector.persist.ConnectorStateStore;
import com.google.enterprise.connector.persist.ConnectorTypeNotFoundException;
import com.google.enterprise.connector.persist.PersistentStoreException;
import com.google.enterprise.connector.scheduler.Schedule;
import com.google.enterprise.connector.scheduler.Scheduler;
import com.google.enterprise.connector.spi.AuthenticationIdentity;
import com.google.enterprise.connector.spi.AuthenticationManager;
import com.google.enterprise.connector.spi.AuthenticationResponse;
import com.google.enterprise.connector.spi.AuthorizationManager;
import com.google.enterprise.connector.spi.AuthorizationResponse;
import com.google.enterprise.connector.spi.ConfigureResponse;
import com.google.enterprise.connector.spi.ConnectorType;
import com.google.enterprise.connector.spi.RepositoryLoginException;
import com.google.enterprise.connector.spi.RepositoryException;
import com.google.enterprise.connector.spi.SimpleAuthenticationIdentity;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 */
public class ProductionManager implements Manager {
  private static final Logger LOGGER =
      Logger.getLogger(ProductionManager.class.getName());

  Instantiator instantiator;
  ConnectorScheduleStore connectorScheduleStore;
  ConnectorStateStore connectorStateStore;
  Scheduler scheduler;

  public ProductionManager() {
  }

  /**
   * Set the scheduler.
   *
   * @param scheduler the scheduler to set.
   */
  public void setScheduler(Scheduler scheduler) {
    this.scheduler = scheduler;
  }

  /**
   * @param instantiator the instantiator to set
   */
  public void setInstantiator(Instantiator instantiator) {
    this.instantiator = instantiator;
  }

  /* @Override */
  public boolean authenticate(String connectorName,
      AuthenticationIdentity identity) {
    boolean result = false;

    try {
      AuthenticationManager authnManager =
          instantiator.getAuthenticationManager(connectorName);
      AuthenticationResponse authenticationResponse;
      // Some connectors don't implement the AuthenticationManager interface so
      // we need to check.
      if (authnManager != null) {
        authenticationResponse = authnManager.authenticate(identity);
      } else {
        authenticationResponse = new AuthenticationResponse(false, null);
      }
      result = authenticationResponse.isValid();
    } catch (ConnectorNotFoundException e) {
      LOGGER.log(Level.WARNING, "Connector " + connectorName + " Not Found: ",
          e);
    } catch (InstantiatorException e) {
      LOGGER.log(Level.WARNING, "Instantiator: ", e);
    } catch (RepositoryLoginException e) {
      LOGGER.log(Level.WARNING, "Login: ", e);
    } catch (RepositoryException e) {
      LOGGER.log(Level.WARNING, "Repository: ", e);
    }

    return result;
  }

  /* @Override */
  public Set authorizeDocids(String connectorName, List docidList,
      String username) {
    Set result = new HashSet();
    try {
      AuthorizationManager authzManager =
          instantiator.getAuthorizationManager(connectorName);
      AuthenticationIdentity identity =
        new SimpleAuthenticationIdentity(username);
      Collection results = authzManager.authorizeDocids(docidList, identity);
      Iterator iter = results.iterator();
      while (iter.hasNext()) {
        AuthorizationResponse response = (AuthorizationResponse) iter.next();
        if (response.isValid()) {
          result.add(response.getDocid());
        }
      }
    } catch (ConnectorNotFoundException e) {
      LOGGER.log(Level.WARNING, "Connector " + connectorName + " Not Found: ",
          e);
    } catch (InstantiatorException e) {
      LOGGER.log(Level.WARNING, "Instantiator: ", e);
    } catch (RepositoryException e) {
      LOGGER.log(Level.WARNING, "Repository: ", e);
    }

    return result;
  }

  /* @Override */
  public ConfigureResponse getConfigForm(String connectorTypeName,
      String language) throws ConnectorTypeNotFoundException {
    ConnectorType connectorType =
        instantiator.getConnectorType(connectorTypeName);
    Locale locale = I18NUtil.getLocaleFromStandardLocaleString(language);
    return connectorType.getConfigForm(locale);
  }

  /* @Override */
  public ConfigureResponse getConfigFormForConnector(String connectorName,
      String language) throws ConnectorNotFoundException, InstantiatorException {
    String connectorTypeName = instantiator.getConnectorTypeName(connectorName);
    Locale locale = I18NUtil.getLocaleFromStandardLocaleString(language);
    ConfigureResponse response =
        instantiator.getConfigFormForConnector(connectorName,
            connectorTypeName, locale);
    return response;
  }

  /* @Override */
  public ConnectorStatus getConnectorStatus(String connectorName) {
    String connectorTypeName = null;
    try {
      connectorTypeName = instantiator.getConnectorTypeName(connectorName);
      String schedule = instantiator.getConnectorSchedule(connectorName);
      // TODO: resolve the third parameter - we need to give status a meaning
      return new ConnectorStatus(connectorName, connectorTypeName, 0, schedule);
    } catch (ConnectorNotFoundException e) {
      // TODO: this should become part of the signature - so we should just
      // let this exception bubble up
      LOGGER.log(Level.WARNING, "Connector type " + connectorTypeName
          + " Not Found: ", e);
      throw new IllegalArgumentException();
    }
  }

  /* @Override */
  public List getConnectorStatuses() {
    List result = new ArrayList();
    String connectorName;
    Iterator iter = instantiator.getConnectorNames();
    while (iter.hasNext()) {
      connectorName = (String) iter.next();
      result.add(getConnectorStatus(connectorName));
    }
    return result;
  }

  /* @Override */
  public Set getConnectorTypeNames() {
    Set result = new TreeSet();
    for (Iterator i = instantiator.getConnectorTypeNames(); i.hasNext();) {
      result.add(i.next());
    }
    return result;
  }

  /* @Override */
  public ConnectorType getConnectorType(String typeName)
      throws ConnectorTypeNotFoundException {
    return instantiator.getConnectorType(typeName);
  }

  /* @Override */
  public ConfigureResponse setConnectorConfig(String connectorName,
      String connectorTypeName, Map configData, String language, boolean update)
      throws ConnectorNotFoundException, PersistentStoreException,
      InstantiatorException {
    Locale locale = I18NUtil.getLocaleFromStandardLocaleString(language);
    ConfigureResponse resp =
        instantiator.setConnectorConfig(connectorName, connectorTypeName,
            configData, locale, update);
    return resp;
  }

  /* @Override */
  public void setConnectorManagerConfig(String feederGateHost,
      int feederGatePort) throws PersistentStoreException {
    try {
      Context.getInstance().setConnectorManagerConfig(feederGateHost,
          feederGatePort);
    } catch (InstantiatorException e) {
      throw new PersistentStoreException(e);
    }
  }

  /* @Override */
  public void setSchedule(String connectorName, int load, int retryDelayMillis,
      String timeIntervals)
      throws ConnectorNotFoundException, PersistentStoreException {
    Schedule schedule = new Schedule(connectorName + ":" + load + ":"
            + retryDelayMillis + ":" + timeIntervals);
    String connectorSchedule = schedule.toString();
    instantiator.setConnectorSchedule(connectorName, connectorSchedule);
  }

  /* @Override */
  public void removeConnector(String connectorName)
      throws InstantiatorException {
    instantiator.removeConnector(connectorName);
    scheduler.removeConnector(connectorName);
  }

  /* @Override */
  public void restartConnectorTraversal(String connectorName)
      throws ConnectorNotFoundException, InstantiatorException {
    scheduler.removeConnector(connectorName);
    instantiator.restartConnectorTraversal(connectorName);
  }

  /* @Override */
  public Map getConnectorConfig(String connectorName)
      throws ConnectorNotFoundException {
    return instantiator.getConnectorConfig(connectorName);
  }
}
