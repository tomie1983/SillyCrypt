// IWorkProfileManageService.aidl
package com.libsillycrypt.workprofile.services;

import com.libsillycrypt.workprofile.domain.entities.ApplicationInfoWrapper;
import com.libsillycrypt.workprofile.services.IAppInstallCallback;
import com.libsillycrypt.workprofile.services.IStartActivityProxy;

interface IWorkProfileManageService {
    void ping();
    void stopShelterService(boolean kill);
    boolean deleteWorkProfile();
    void installApp(in ApplicationInfoWrapper app, IAppInstallCallback callback);
    void installApps(in List<ApplicationInfoWrapper> apps, IAppInstallCallback callback);
    void uninstallApp(in ApplicationInfoWrapper app, IAppInstallCallback callback);
    void setStartActivityProxy(in IStartActivityProxy proxy);
}