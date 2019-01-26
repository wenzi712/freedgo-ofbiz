/*******************************************************************************
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 *******************************************************************************/

package org.ofbiz.sso.commons;

import org.ofbiz.base.util.UtilHttp;
import org.ofbiz.base.util.UtilMisc;
import org.ofbiz.base.util.UtilProperties;
import org.ofbiz.base.util.UtilXml;
import org.ofbiz.entity.GenericEntityException;
import org.ofbiz.entity.GenericValue;
import org.ofbiz.service.GenericServiceException;
import org.ofbiz.service.LocalDispatcher;
import org.ofbiz.service.ModelService;
import org.ofbiz.sso.SSOLoginWorker;
import org.ofbiz.webapp.stats.VisitHandler;
import org.w3c.dom.Element;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.util.Map;

import static org.ofbiz.base.util.UtilGenerics.checkMap;

/**
 * The abstract Authentication Handler.
 *
 * The ACL of a user is still controlled by OFBiz.<p>
 *
 */
public abstract class AbstractOFBizAuthenticationHandler implements InterfaceOFBizAuthenticationHandler {

    /**
     * Public constructor, initializes some required member variables.<p>
     */
    public AbstractOFBizAuthenticationHandler() {

    }

    public Object getPartyId(Element rootElement, GenericValue result) {
        Object partyId = UtilXml.childElementValue(rootElement, "AutoPartyId", "admin");
        return partyId;
    }

    public Object getSecurityGroup(Element rootElement, GenericValue result) {
        Object securityGroupId = UtilXml.childElementValue(rootElement, "AutoSecurityGroupId", "SYSTEMMGR");
        return securityGroupId;
    }

    public String login(HttpServletRequest request, HttpServletResponse response, Element rootElement) throws Exception {

        String username = request.getParameter("USERNAME");
        String password = request.getParameter("PASSWORD");

        GenericValue result = getBaseSearchResult(username, password, rootElement, true, request);
        if (result != null) {
            return login(request, response, username, password, rootElement, result);
        }
        return "error";
    }

    public String logout(HttpServletRequest request, HttpServletResponse response, Element rootElement) {
        return "success";
    }

    public abstract GenericValue getBaseSearchResult(String username, String password, Element rootElement, boolean bindRequired, HttpServletRequest request) throws GenericEntityException, IllegalAccessException, InstantiationException, ClassNotFoundException;


    /**
     * cas登陆成功后，现在验证数据在user_login是否存在如果不存在则插入到数据库的用户标准
     * @param request
     * @param response
     * @param username
     * @param password
     * @param rootElement
     * @param result
     * @return
     * @throws Exception
     */
    public String login(HttpServletRequest request, HttpServletResponse response, String username, String password, Element rootElement, GenericValue result) throws Exception {

        password = (String) result.get("currentPassword");
        HttpSession session = request.getSession();
        // get the visit id to pass to the userLogin for history
        String visitId = VisitHandler.getVisitId(session);
        LocalDispatcher dispatcher = (LocalDispatcher) request.getAttribute("dispatcher");

        Map<String, Object> loginResult = null;
        try {
            loginResult = dispatcher.runSync("userLogin", UtilMisc.toMap("isSSO",true,"login.username", username, "login.password", password, "visitId", visitId, "locale", UtilHttp.getLocale(request)));
        } catch (GenericServiceException e) {
            throw new GenericServiceException(e.getLocalizedMessage());
        }
        if (ModelService.RESPOND_SUCCESS.equals(loginResult.get(ModelService.RESPONSE_MESSAGE))) {
            GenericValue userLogin = (GenericValue) loginResult.get("userLogin");
            Map<String, Object> userLoginSession = checkMap(loginResult.get("userLoginSession"), String.class, Object.class);
            return SSOLoginWorker.doMainLogin(request, response, userLogin, userLoginSession);
        } else {
            Map<String, String> messageMap = UtilMisc.toMap("errorMessage", (String) loginResult.get(ModelService.ERROR_MESSAGE));
            String errMsg = UtilProperties.getMessage(SSOLoginWorker.resourceWebapp, "loginevents.following_error_occurred_during_login", messageMap, UtilHttp.getLocale(request));
            throw new Exception(errMsg);
        }
    }

    /**
     * An HTTP WebEvent handler that checks to see is a userLogin is logged out.
     * If yes, the user is forwarded to the login page.
     *
     * @param request The HTTP request object for the current JSP or Servlet request.
     * @param response The HTTP response object for the current JSP or Servlet request.
     * @param rootElement Element root element of ldap config file
     * @return true if the user has logged out from ldap; otherwise, false.
     */
    public boolean hasBaseLoggedOut(HttpServletRequest request, HttpServletResponse response, Element rootElement) {
        return false;
    }
}