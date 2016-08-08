package org.ovirtChina.enginePlugin.vmBackupScheduler.bll;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.TimerTask;
import java.util.UUID;

import org.apache.http.client.ClientProtocolException;
import org.ovirt.engine.sdk.Api;
import org.ovirt.engine.sdk.decorators.DataCenter;
import org.ovirt.engine.sdk.decorators.Event;
import org.ovirt.engine.sdk.decorators.StorageDomain;
import org.ovirt.engine.sdk.decorators.VM;
import org.ovirt.engine.sdk.decorators.VMs;
import org.ovirt.engine.sdk.entities.Snapshot;

import org.ovirt.engine.sdk.exceptions.ServerException;
import org.ovirtChina.enginePlugin.vmBackupScheduler.common.EngineEventSeverity;
import org.ovirtChina.enginePlugin.vmBackupScheduler.common.Task;
import org.ovirtChina.enginePlugin.vmBackupScheduler.common.TaskStatus;
import org.ovirtChina.enginePlugin.vmBackupScheduler.common.VmPolicy;
import org.ovirtChina.enginePlugin.vmBackupScheduler.dao.DbFacade;
import org.ovirtChina.enginePlugin.vmBackupScheduler.utils.ConfigProvider;
import org.slf4j.Logger;

public abstract class TimerSDKTask extends TimerTask {

    protected static Logger log;
    protected static Api api = null;
    protected int interval = 10000;
    protected long taskTimeoutMin = 60L;

    public TimerSDKTask(Api api1) {
        super();
        api = api1;
        interval = Integer.parseInt(ConfigProvider.getConfig().getProperty(ConfigProvider.QUERY_INTERVAL_M));
        taskTimeoutMin = Integer.parseInt(ConfigProvider.getConfig().getProperty(ConfigProvider.TASK_TIMEOUT_MIN));
    }

    @Override
    public void run() {
        try {
            peformAction();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    protected void setTaskStatus(Task taskToExec, TaskStatus taskStatus) {
        taskToExec.setTaskStatus(taskStatus.getValue());
        taskToExec.setLastUpdate(new Date());
        DbFacade.getInstance().getTaskDAO().update(taskToExec);
    }

    protected StorageDomain getIsoDomainToExport(VM vm) throws ClientProtocolException, ServerException, IOException {
        DataCenter dataCenter = api.getDataCenters().getById(api.getClusters().getById(vm.getCluster().getId()).getDataCenter().getId());
        for (StorageDomain sd : api.getStorageDomains().list("datacenter=" + dataCenter.getName(), true, null)) {
            if (sd.getType().equals("export")) {
                return sd;
            }
        }
        return null;
    }

    protected void querySnapshot(Task taskToExec, VM vm) throws ClientProtocolException, ServerException, IOException, InterruptedException {
        while(!api.getVMs().get(UUID.fromString(vm.getId())).getSnapshots().getById(taskToExec.getBackupName()).getSnapshotStatus().equals("ok")) {
            log.info("vm: " + vm.getName() + " is snapshoting, waiting for next query...");
            Thread.sleep(interval);
        }
    }

    protected VM createSnapshot(Task taskToExec, String desc) throws ClientProtocolException, ServerException, IOException {
        Snapshot snap = new Snapshot();
        VM vm = api.getVMs().get(taskToExec.getVmID());
        snap.setVm(vm);
        snap.setDescription(desc);
        String snapshotId = null;
        try{
            snapshotId = vm.getSnapshots().add(snap).getId();
        } catch (ServerException e) {
            if (new Date().getTime() - taskToExec.getCreateTime().getTime() <
                    Long.parseLong(ConfigProvider.getConfig().getProperty(ConfigProvider.SNAPSHOT_DELAY_MIN)) * 60000L) {
                log.warn("snapshot: " + desc + " of vm: " + vm.getName() + " has failed, will try in next schedule.");
                setTaskStatus(taskToExec, TaskStatus.RETRYING);
            } else {
                log.error("snapshot: " + desc + " of vm: " + vm.getName() + " has failed and exceeded delay config, mark as failed.");
                setTaskStatus(taskToExec, TaskStatus.FAILED);
            }
            return null;
        }
        taskToExec.setBackupName(snapshotId);
        setTaskStatus(taskToExec, TaskStatus.EXECUTING);
        return vm;
    }

    protected void addEngineEvent(EngineEventSeverity severity, String message)
            throws ClientProtocolException, ServerException, IOException, InterruptedException {
        addEngineEvent(severity, message, 0);
    }
    private void addEngineEvent(EngineEventSeverity severity, String message, int retryCount)
            throws ClientProtocolException, ServerException, IOException, InterruptedException {
        Event event = new Event(null);
        event.setSeverity(severity.name());
        event.setOrigin("Engine-vm-backup");
        event.setCustomId(returnSeconds());
        event.setDescription("Engine-vm-backup: " + message);
        try{
            api.getEvents().add(event);
        } catch (ServerException e) {
            Thread.sleep(1000);
            if (retryCount > 3) {
                log.error("failed to add external event to engine with severity: " + severity.name() + " and message: " + message);
                return;
            }
            log.debug("retrying adding external event to engine with severity: " + severity.name() + " and message: " + message);
            addEngineEvent(severity, message, retryCount+1);
            return;
        }
    }

    private static int returnSeconds() {
        Calendar calendar1 = Calendar.getInstance();
        calendar1.set(2014, Calendar.DECEMBER, 1, 0, 0, 0);
        long milliseconds1 = calendar1.getTimeInMillis();
        long milliseconds2 = System.currentTimeMillis();
        long diff = milliseconds2 - milliseconds1;
        long seconds = diff / 1000;
        return (int) seconds;
    }

    protected void deletedb(){
    	VMs vms = api.getVMs();
    	List<VM> list1;
		try {
			list1 = vms.list();
			List listvmid=new ArrayList(list1.size());
	    	for(int i=0;i<list1.size();i++){
	    		String str =list1.get(i).getId();
	    		listvmid.add(i, list1.get(i).getId());
	    	}
	    	List<VmPolicy> list2 = DbFacade.getInstance().getVmPolicyDAO().selectallid();
	    	list2.removeAll(listvmid);
	    	for(int i=0;i<list2.size();i++){
	    		DbFacade.getInstance().getVmPolicyDAO().deleteVmPolicy(list2.get(i).getVmID());
	    	}
		} catch (ServerException | IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

    }
    
    protected void deleteSnapshot(Task task) throws ClientProtocolException, ServerException, IOException, InterruptedException {
		VM vm = api.getVMs().get(task.getVmID());
		String vm_status = vm.getStatus().getState();
        if (vm_status.equals("down") || vm_status.equals("up")) {
            String message = "delete CreateSnapshot backup for vm: " + vm.getName() + " has initiated.";
            try{
                vm.getSnapshots().getById(task.getBackupName()).delete();
            } catch (ServerException e) {
                if(e.getCode() == 409 && "Conflict".equals(e.getReason())) {
                    log.debug("delaying deletion of snapshot: " + task.getBackupName() + " for vm: " + task.getVmID()
                        + ", because vm is making snapshot tasks");
                    return;
                }else{
                    deleteTaskRecord(EngineEventSeverity.normal, message, task);
                }
            }
            deleteTaskRecord(EngineEventSeverity.normal, message, task);
        } else {
            log.debug("cancel deletion of snapshot: " + task.getBackupName() + " for vm: " + task.getVmID()
                + ", because vm is not in 'down' or 'up' status.");
        }
    }

    protected void deleteTaskRecord(EngineEventSeverity severity, String message, Task task)
            throws ClientProtocolException, ServerException, IOException, InterruptedException {
        log.info(message);
        addEngineEvent(severity, message);
        DbFacade.getInstance().getTaskDAO().delete(task.getVmID(), task.getBackupName());
    }

    protected abstract void peformAction() throws Exception;

}
