/*  Copyright (c) 2008 Konrad-Zuse-Zentrum fuer Informationstechnik Berlin.

This file is part of XtreemFS. XtreemFS is part of XtreemOS, a Linux-based
Grid Operating System, see <http://www.xtreemos.eu> for more details.
The XtreemOS project has been developed with the financial support of the
European Commission's IST program under contract #FP6-033576.

XtreemFS is free software: you can redistribute it and/or modify it under
the terms of the GNU General Public License as published by the Free
Software Foundation, either version 2 of the License, or (at your option)
any later version.

XtreemFS is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
GNU General Public License for more details.

You should have received a copy of the GNU General Public License
along with XtreemFS. If not, see <http://www.gnu.org/licenses/>.
 */
/*
 * AUTHORS: Jan Stender (ZIB)
 */
package org.xtreemfs.new_mrc.operations;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.xtreemfs.common.TimeSync;
import org.xtreemfs.common.uuids.UnknownUUIDException;
import org.xtreemfs.foundation.json.JSONException;
import org.xtreemfs.foundation.json.JSONParser;
import org.xtreemfs.mrc.MRCConfig;
import org.xtreemfs.mrc.brain.UserException;
import org.xtreemfs.new_mrc.MRCException;
import org.xtreemfs.new_mrc.ac.FileAccessManager;
import org.xtreemfs.new_mrc.dbaccess.AtomicDBUpdate;
import org.xtreemfs.new_mrc.dbaccess.DatabaseException;
import org.xtreemfs.new_mrc.dbaccess.StorageManager;
import org.xtreemfs.new_mrc.metadata.ACLEntry;
import org.xtreemfs.new_mrc.metadata.FileMetadata;
import org.xtreemfs.new_mrc.metadata.StripingPolicy;
import org.xtreemfs.new_mrc.metadata.XLocList;
import org.xtreemfs.new_mrc.osdselection.OSDStatusManager;
import org.xtreemfs.new_mrc.utils.Converter;
import org.xtreemfs.new_mrc.volumes.metadata.VolumeInfo;

public class MRCOpHelper {
    
    protected enum SysAttrs {
            locations,
            file_id,
            object_type,
            url,
            owner,
            group,
            default_sp,
            ac_policy_id,
            osdsel_policy_id,
            osdsel_policy_args,
            read_only,
            free_space
    }
    
    public static void updateFileTimes(long parentId, FileMetadata file, boolean setATime,
        boolean setCTime, boolean setMTime, StorageManager sMan, AtomicDBUpdate update)
        throws DatabaseException {
        
        if(parentId == -1)
            return;
        
        int currentTime = (int) (TimeSync.getGlobalTime() / 1000);
        
        if (setATime)
            file.setAtime(currentTime);
        if (setCTime)
            file.setCtime(currentTime);
        if (setMTime)
            file.setMtime(currentTime);
        
        sMan.setMetadata(parentId, file.getFileName(), file, FileMetadata.FC_METADATA, update);
    }
    
    public static Map<String, Object> createStatInfo(FileAccessManager faMan, FileMetadata file,
        String ref, String volumeId, String userId, List<String> groupIds, XLocList xLocList,
        Map<String, Object> xAttrs, Iterator<ACLEntry> acl) throws UserException, MRCException {
        
        Map<String, Object> map = new HashMap<String, Object>();
        map.put("fileId", volumeId + ":" + file.getId());
        map.put("objType", ref != null ? 3 : file.isDirectory() ? 2 : 1);
        map.put("ownerId", file.getOwnerId());
        map.put("groupId", file.getOwningGroupId());
        map.put("size", ref != null ? ref.length() : file.isDirectory() ? 0 : file.getSize());
        map.put("epoch", file.isDirectory() ? 0 : file.getEpoch());
        map.put("atime", file.getAtime());
        map.put("ctime", file.getCtime());
        map.put("mtime", file.getMtime());
        map.put("linkCount", file.isDirectory()? 1: (long) file.getLinkCount());
        
        if (ref != null)
            map.put("linkTarget", ref);
        if (xLocList != null)
            map.put("replicas", Converter.xLocListToList(xLocList));
        if (xAttrs != null)
            map.put("xAttrs", xAttrs);
        if (acl != null)
            map.put("acl", Converter.aclToMap(acl));
        
        map.put("posixAccessMode", faMan.getPosixAccessMode(volumeId, file.getId(), userId,
            groupIds));
        
        return map;
    }
    
    public static String getSysAttrValue(MRCConfig config, StorageManager sMan,
        OSDStatusManager osdMan, VolumeInfo volume, Path p, FileMetadata file, String keyString)
        throws DatabaseException, JSONException, UnknownUUIDException {
        
        SysAttrs key = null;
        try {
            key = SysAttrs.valueOf(keyString);
        } catch (IllegalArgumentException exc) {
            // ignore, will be handled by the 'default' case
        }
        
        switch (key) {
        
        case locations:
            return file.isDirectory() ? "" : JSONParser.writeJSON(Converter.xLocListToList(file
                    .getXLocList()));
        case file_id:
            return volume.getId() + ":" + file.getId();
        case object_type:
            String ref = sMan.getSoftlinkTarget(file.getId());
            return ref != null ? "3" : file.isDirectory() ? "2" : "1";
        case url:
            return "uuid:" + config.getUUID().toString() + "/" + p.toString();
        case owner:
            return file.getOwnerId();
        case group:
            return file.getOwningGroupId();
        case default_sp:
            if (!file.isDirectory())
                return "";
            StripingPolicy sp = sMan.getDefaultStripingPolicy(file.getId());
            if (sp == null)
                return "";
            return sp.toString();
        case ac_policy_id:
            return file.getId() == 1 ? volume.getAcPolicyId() + "" : "";
        case osdsel_policy_id:
            return file.getId() == 1 ? volume.getOsdPolicyId() + "" : "";
        case osdsel_policy_args:
            return file.getId() == 1 ? (volume.getOsdPolicyArgs() == null ? "" : volume
                    .getOsdPolicyArgs()) : "";
        case read_only:
            if (!file.isDirectory())
                return String.valueOf(false);
            
            return String.valueOf(file.isReadOnly());
        case free_space:
            return file.getId() == 1 ? String.valueOf(osdMan.getFreeSpace(volume.getId())) : "";
        }
        
        return "";
    }
    
    // public static void setSysAttrValue(StorageManager sMan, VolumeManager
    // vMan,
    // VolumeInfo volume, FileMetadata file, String keyString, String value,
    // AtomicDBUpdate update)
    // throws UserException, IOException {
    //        
    // SysAttrs key = null;
    // try {
    // key = SysAttrs.valueOf(keyString);
    // } catch (IllegalArgumentException exc) {
    // // ignore, will be handled by the 'default' case
    // }
    //        
    // switch (key) {
    //        
    // case locations:
    //
    // // explicitly setting X-Locations lists is only permitted for files
    // // that haven't yet been assigned an X-Locations list!
    // if (file.getXLocList() != null)
    // throw new UserException(ErrNo.EPERM,
    // "cannot set X-Locations: OSDs have been assigned already");
    //            
    // try {
    // // parse the X-Locations list, ensure that it is correctly
    // // formatted and consistent
    // XLocList newXLoc = Converter.listToXLocList(sMan, (List<Object>)
    // JSONParser
    // .parseJSON(new JSONString(value)));
    //                
    // if (!BrainHelper.isConsistent(newXLoc))
    // throw new UserException(ErrNo.EINVAL, "inconsistent X-Locations list:"
    // + "at least one OSD occurs more than once");
    //                
    // file = sMan.createFileMetadata();
    // sMan.setMetadata(parentId, fileName, file, FileMetadata.XLOC_METADATA,
    // update);
    // sMan.setXLocationsList(file.getId(), newXLoc);
    //                
    // } catch (JSONException exc) {
    // throw new UserException(ErrNo.EINVAL, "invalid X-Locations-List: " +
    // value);
    // }
    //            
    // break;
    //        
    // case default_sp:
    //
    // if (!file.isDirectory())
    // throw new UserException(ErrNo.EPERM,
    // "default striping policies can only be set on volumes and directories");
    //            
    // try {
    // Map<String, Object> sp = null;
    // if (!value.equals("null")) {
    // StringTokenizer st = new StringTokenizer(value, ", \t");
    // sp = new HashMap<String, Object>();
    // sp.put("policy", st.nextToken());
    // sp.put("stripe-size", Long.parseLong(st.nextToken()));
    // sp.put("width", Long.parseLong(st.nextToken()));
    // }
    //                
    // if (file.getId() == 1 && sp == null)
    // throw new UserException(ErrNo.EPERM,
    // "cannot remove volume default striping policy");
    //                
    // sMan.setStripingPolicy(file.getId(), sp);
    // } catch (NumberFormatException exc) {
    // throw new UserException(ErrNo.EINVAL, "invalid default striping policy: "
    // + value);
    // }
    //            
    // break;
    //        
    // case osdsel_policy_id:
    //
    // if (file.getId() != 1)
    // throw new UserException(ErrNo.EINVAL,
    // "OSD selection policies can only be set on volumes");
    //            
    // try {
    // long newPol = Long.parseLong(value);
    // volume.setOsdPolicyId(newPol);
    // sliceMan.notifyVolumeChangeListeners(VolumeChangeListener.MOD_CHANGED,
    // volume);
    //                
    // } catch (NumberFormatException exc) {
    // throw new UserException(ErrNo.EINVAL, "invalid OSD selection policy: " +
    // value);
    // }
    //            
    // break;
    //        
    // case osdsel_policy_args:
    //
    // if (file.getId() != 1)
    // throw new UserException(ErrNo.EINVAL,
    // "OSD selection policies can only be set and configured on volumes");
    //            
    // volume.setOsdPolicyArgs(value);
    // sliceMan.notifyVolumeChangeListeners(VolumeChangeListener.MOD_CHANGED,
    // volume);
    //            
    // break;
    //        
    // case read_only:
    //
    // if (!(file instanceof FileEntity))
    // throw new UserException(ErrNo.EPERM, "only files can be made read-only");
    //            
    // boolean readOnly = Boolean.valueOf(value);
    //            
    // FileEntity fileAsFile = (FileEntity) file;
    // if (!readOnly && fileAsFile.getXLocationsList().getReplicas().length > 1)
    // throw new UserException(ErrNo.EPERM,
    // "read-only flag cannot be removed from files with multiple replicas");
    //            
    // sMan.setReadOnly(file.getId(), readOnly);
    //            
    // break;
    //        
    // default:
    // throw new UserException(ErrNo.EINVAL, "system attribute '" + key
    // + "' unknown or immutable");
    // }
    // }
    
}
