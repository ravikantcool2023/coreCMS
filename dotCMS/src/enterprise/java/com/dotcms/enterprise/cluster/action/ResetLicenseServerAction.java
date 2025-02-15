/* 
* Licensed to dotCMS LLC under the dotCMS Enterprise License (the
* “Enterprise License”) found below 
* 
* Copyright (c) 2023 dotCMS Inc.
* 
* With regard to the dotCMS Software and this code:
* 
* This software, source code and associated documentation files (the
* "Software")  may only be modified and used if you (and any entity that
* you represent) have:
* 
* 1. Agreed to and are in compliance with, the dotCMS Subscription Terms
* of Service, available at https://www.dotcms.com/terms (the “Enterprise
* Terms”) or have another agreement governing the licensing and use of the
* Software between you and dotCMS. 2. Each dotCMS instance that uses
* enterprise features enabled by the code in this directory is licensed
* under these agreements and has a separate and valid dotCMS Enterprise
* server key issued by dotCMS.
* 
* Subject to these terms, you are free to modify this Software and publish
* patches to the Software if you agree that dotCMS and/or its licensors
* (as applicable) retain all right, title and interest in and to all such
* modifications and/or patches, and all such modifications and/or patches
* may only be used, copied, modified, displayed, distributed, or otherwise
* exploited with a valid dotCMS Enterprise license for the correct number
* of dotCMS instances.  You agree that dotCMS and/or its licensors (as
* applicable) retain all right, title and interest in and to all such
* modifications.  You are not granted any other rights beyond what is
* expressly stated herein.  Subject to the foregoing, it is forbidden to
* copy, merge, publish, distribute, sublicense, and/or sell the Software.
* 
* THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS
* OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF
* MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT.
* IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY
* CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT,
* TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE
* SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
* 
* For all third party components incorporated into the dotCMS Software,
* those components are licensed under the original license provided by the
* owner of the applicable component.
*/

/**
 * 
 */
package com.dotcms.enterprise.cluster.action;

import java.util.Date;

import com.dotcms.enterprise.LicenseUtil;
import com.dotcms.enterprise.cluster.action.model.ServerActionBean;
import com.dotmarketing.db.HibernateUtil;
import com.dotmarketing.exception.DotHibernateException;
import com.dotmarketing.util.Logger;
import com.dotmarketing.util.json.JSONException;
import com.dotmarketing.util.json.JSONObject;

/**
 * @author oarrieta
 *
 */
public class ResetLicenseServerAction implements ServerAction {
	
	public static String ACTION_ID = "RESET_LICENSE";

	/* (non-Javadoc)
	 * @see com.dotcms.enterprise.cluster.action.ServerAction#getServerActionID()
	 */
	@Override
	public String getServerActionID() {
		return ACTION_ID;
	}

	/* (non-Javadoc)
	 * @see com.dotcms.enterprise.cluster.action.ServerAction#run()
	 */
	@Override
	public JSONObject run() {
		
		JSONObject jsonObject = new JSONObject();
		
		try {
			HibernateUtil.startTransaction();
			LicenseUtil.freeLicenseOnRepo();
			HibernateUtil.commitTransaction();
			
			Logger.info(ResetLicenseServerAction.class, "License From Repo Freed");
			jsonObject.put(ServerAction.SUCCESS_STATE, "License From Repo Freed");
			
		} catch (Exception e) {
			
            try {
            	Logger.error(ResetLicenseServerAction.class, "Can NOT free license ", e);
                jsonObject.put(ServerAction.ERROR_STATE, "Can NOT free license");
                HibernateUtil.rollbackTransaction();
                
            } catch (DotHibernateException dotHibernateException) {
                Logger.warn(ResetLicenseServerAction.class, "Can NOT rollback", dotHibernateException);
            } catch (JSONException ex) {
            	Logger.error(ResetLicenseServerAction.class, "Can NOT write JSONObject ", ex);
			}
		} finally {
			HibernateUtil.closeSessionSilently();
		}
		
		return jsonObject;
		
	}

	/* (non-Javadoc)
	 * @see com.dotcms.enterprise.cluster.action.ServerAction#getNewServerAction(java.lang.String, java.lang.String, java.lang.Long)
	 */
	@Override
	public ServerActionBean getNewServerAction(String originatorServerID, String receptorServerID, Long timeoutSeconds) {
		ServerActionBean serverActionBean = new ServerActionBean();
		serverActionBean.setOriginatorId(originatorServerID);
		serverActionBean.setServerId(receptorServerID);
		serverActionBean.setFailed(false);
		serverActionBean.setResponse(null);
		serverActionBean.setServerActionId(ACTION_ID);
		serverActionBean.setCompleted(false);
		serverActionBean.setEnteredDate(new Date());
		serverActionBean.setTimeOutSeconds(timeoutSeconds);
		
		return serverActionBean;
	}

}
