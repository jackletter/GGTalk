﻿using System;
using System.Collections.Generic;
using System.Text;
using ESBasic;
using ESBasic.ObjectManagement.Managers;
using ESBasic.Collections;
using ESPlus.Application.P2PSession.Passive;
using ESPlus.Core;
using ESFramework.Core;
using ESFramework.Engine.Tcp.Passive;
using ESFramework;
using ESFramework.Passive;
using ESPlus.Application;
using ESPlus.Application.CustomizeInfo.Passive;
using ESPlus.Serialization;
using ESPlus.Rapid;
using ESPlus.Application.Group;

namespace ESFramework.Boost.DynamicGroup.Passive
{
    /// <summary>
    /// IGroupOutter 的实现。
    /// (1)所有组成员都是在线的。
    /// (2)尽可能使用P2P通道发送广播信息。
    /// (3)在从服务器获取组成员时，尝试与在线的组成员创建P2P连接。
    /// </summary>
    public class DynamicGroupOutter : IDynamicGroupOutter
    {
        //本地组成员列表缓存。
        private ObjectManager<string, SortedArray<string>> groupCache = new ObjectManager<string, SortedArray<string>>(); 
       
        #region property
        #region GroupInfoTypes
        private GroupInfoTypes groupInfoTypes = new GroupInfoTypes();
        public GroupInfoTypes GroupInfoTypes
        {
            set { groupInfoTypes = value; }
            get { return groupInfoTypes; }
        }
        #endregion
       
        #region RapidPassiveEngine
        private IRapidPassiveEngine rapidPassiveEngine;
        public IRapidPassiveEngine RapidPassiveEngine
        {
            set { rapidPassiveEngine = value; }
        }
        #endregion

        #region TryP2PWhenGroupmateConnected
        private bool tryP2PWhenGroupmateConnected = true;
        /// <summary>
        /// 当组友上线时，是否自动进行P2P连接。默认值为true。
        /// </summary>
        public bool TryP2PWhenGroupmateConnected
        {
            get { return tryP2PWhenGroupmateConnected; }
            set { tryP2PWhenGroupmateConnected = value; }
        }
        #endregion
        #endregion

        #region event
        public event ESBasic.CbGeneric<string, string> SomeoneJoinGroup; //GroupID - MemberID
        public event ESBasic.CbGeneric<string, string> SomeoneQuitGroup; 
        public event ESBasic.CbGeneric<string> GroupmateOffline;       
        public event CbGeneric<string, string, int, byte[]> BroadcastReceived; 
        #endregion       

        #region internal
        internal void SomeoneJoinGroupNotify(string groupID, string memberID)
        {
            SortedArray<string> ary = this.groupCache.Get(groupID);
            if (ary != null)
            {
                ary.Add(memberID);
                if (this.tryP2PWhenGroupmateConnected)
                {
                    this.rapidPassiveEngine.P2PController.P2PConnectAsyn(memberID);
                }
            }     

            if (this.SomeoneJoinGroup != null)
            {
                this.SomeoneJoinGroup(groupID, memberID);
            }
        }
        internal void SomeoneQuitGroupNotify(string groupID, string memberID)
        {
            SortedArray<string> ary = this.groupCache.Get(groupID);
            if (ary != null)
            {
                ary.Remove(memberID);
            }
            if (this.SomeoneQuitGroup != null)
            {
                this.SomeoneQuitGroup(groupID, memberID);
            }
        }
        internal void GroupmateOfflineNotify(string memberID)
        {
            foreach (SortedArray<string> ary in this.groupCache.GetAll())
            {
                ary.Remove(memberID);
            }

            if (this.GroupmateOffline != null)
            {
                this.GroupmateOffline(memberID);
            }
        }
        internal void OnBroadcast(string broadcasterID, string groupID, int broadcastType, byte[] broadcastContent)
        {
            if (this.BroadcastReceived != null)
            {
                this.BroadcastReceived(broadcasterID, groupID, broadcastType, broadcastContent);
            }
        }
        internal void OnBroadcastBlob(string broadcasterID, string groupID, int broadcastType, byte[] broadcastContent)
        {
            if (this.BroadcastReceived != null)
            {
                this.BroadcastReceived(broadcasterID, groupID, broadcastType, broadcastContent);
            }
        }
        #endregion                 

        #region SafeGetGroupMembers
        private SortedArray<string> SafeGetGroupMembers(string groupID)
        {
            try
            {
                if (!this.groupCache.Contains(groupID))
                {
                    byte[] response = this.rapidPassiveEngine.CustomizeOutter.Query(this.groupInfoTypes.GetGroupMembers, CompactPropertySerializer.Default.Serialize(new GroupContract(groupID)));
                    List<string> members = CompactPropertySerializer.Default.Deserialize<List<string>>(response, 0);
                    if (members == null)
                    {
                        return null;
                    }
                    this.groupCache.Add(groupID, new SortedArray<string>(members));
                    if (this.tryP2PWhenGroupmateConnected)
                    {
                        foreach (string memberID in members)
                        {
                            if (memberID != this.currentUserID)
                            {
                                this.rapidPassiveEngine.P2PController.P2PConnectAsyn(memberID);
                            }
                        }
                    }
                }

                return this.groupCache.Get(groupID);
            }
            catch
            {
                return null;
            }
        } 
        #endregion       

        #region Initialize
        #region Initialized
        private bool initialized = false;
        public bool Initialized
        {
            get { return initialized; }
        }
        #endregion

        #region Initialize
        private string currentUserID = "";
        public void Initialize(string _currentUserID)
        {
            this.currentUserID = _currentUserID;
            this.rapidPassiveEngine.ConnectionInterrupted += new CbGeneric(engine_ConnectionInterrupted);
            this.rapidPassiveEngine.P2PController.P2PChannelOpened += new CbGeneric<P2PChannelState>(P2PController_P2PChannelOpened);
            this.rapidPassiveEngine.P2PController.P2PChannelClosed += new CbGeneric<P2PChannelState>(P2PController_P2PChannelClosed);
            this.initialized = true;           
        }

        void P2PController_P2PChannelClosed(P2PChannelState state)
        {
            this.P2PChannelReport(false, state.DestUserID);
        }

        void P2PController_P2PChannelOpened(P2PChannelState state)
        {
            this.P2PChannelReport(true, state.DestUserID);
        }

        void engine_ConnectionInterrupted()
        {
            this.groupCache.Clear();
        }
        #endregion

        private void P2PChannelReport(bool open, string destUserID)
        {
            P2PChannelReportContract contract = new P2PChannelReportContract(destUserID);
            int messageType = open ? this.groupInfoTypes.P2PChannelOpen : this.groupInfoTypes.P2PChannelClose;
            this.rapidPassiveEngine.CustomizeOutter.Send(messageType, CompactPropertySerializer.Default.Serialize(contract));            
        }
        #endregion  

        #region JoinGroup
        public void JoinGroup(string groupID)
        {
            this.rapidPassiveEngine.CustomizeOutter.Send(this.groupInfoTypes.Join, CompactPropertySerializer.Default.Serialize(new GroupContract(groupID)));
        } 
        #endregion

        #region Recruit ,Fire
        public bool Recruit(string groupID, string memberID)
        {
            byte[] response = this.rapidPassiveEngine.CustomizeOutter.Query(this.groupInfoTypes.Recruit, CompactPropertySerializer.Default.Serialize(new RecruitOrFireContract(groupID, memberID)));
            bool suc = BitConverter.ToBoolean(response, 0);
            if (suc)
            {
                SortedArray<string> ary = this.groupCache.Get(groupID);
                if (ary != null)
                {
                    ary.Add(memberID);
                }

                if (this.tryP2PWhenGroupmateConnected)
                {
                    this.rapidPassiveEngine.P2PController.P2PConnectAsyn(memberID);
                }
            }
            return suc;
        }

        public void Fire(string groupID, string memberID)
        {
            this.rapidPassiveEngine.CustomizeOutter.Send(this.groupInfoTypes.Fire, CompactPropertySerializer.Default.Serialize(new RecruitOrFireContract(groupID, memberID)));
            SortedArray<string> ary = this.groupCache.Get(groupID);
            if (ary != null)
            {
                ary.Remove(memberID);
            }
        }        
        #endregion

        #region QuitGroup
        public void QuitGroup(string groupID)
        {
            this.rapidPassiveEngine.CustomizeOutter.Send(this.groupInfoTypes.QuitGroup, CompactPropertySerializer.Default.Serialize(new GroupContract(groupID)));
        } 
        #endregion

        #region DestroyGroup
        public void DestroyGroup(string groupID)
        {
            this.rapidPassiveEngine.CustomizeOutter.Send(this.groupInfoTypes.DestroyGroup, CompactPropertySerializer.Default.Serialize(new GroupContract(groupID)));
        } 
        #endregion

        #region GetGroupMembers
        public List<string> GetGroupMembers(string groupID)
        {
            SortedArray<string> ary = this.SafeGetGroupMembers(groupID);
            if (ary == null)
            {
                return null;
            }
            return ary.GetAll();
        } 
        #endregion

        #region GetGroupMembersFromServer
        public List<string> GetGroupMembersFromServer(string groupID)
        {
            byte[] response = this.rapidPassiveEngine.CustomizeOutter.Query(this.groupInfoTypes.GetGroupMembers, CompactPropertySerializer.Default.Serialize(new GroupContract(groupID)));
            List<string> members = CompactPropertySerializer.Default.Deserialize<List<string>>(response, 0);
            return members;
        } 
        #endregion

        #region BroadcastByServer
        private void BroadcastByServer(string groupID, int broadcastType, byte[] broadcastContent, ActionTypeOnChannelIsBusy action)
        {
            BroadcastContract contract = new BroadcastContract(this.currentUserID, groupID, broadcastType, broadcastContent, action);
            this.rapidPassiveEngine.CustomizeOutter.Send(this.groupInfoTypes.BroadcastByServer, CompactPropertySerializer.Default.Serialize(contract));
        } 
        #endregion

        #region BroadcastByP2PChannel
        private void BroadcastByP2PChannel(string groupID, int broadcastType, byte[] broadcastContent, ActionTypeOnChannelIsBusy action)
        {
            BroadcastContract contract = new BroadcastContract(this.currentUserID, groupID, broadcastType, broadcastContent, action);
            byte[] info = CompactPropertySerializer.Default.Serialize(contract);
            SortedArray<string> ary = this.SafeGetGroupMembers(groupID);
            foreach (string memberID in ary.GetAll())
            {
                if (memberID == this.currentUserID)
                {
                    continue;
                }

                if (this.rapidPassiveEngine.P2PController.IsP2PChannelExist(memberID))
                {
                    this.rapidPassiveEngine.CustomizeOutter.SendByP2PChannel(memberID, this.groupInfoTypes.Broadcast, info, ActionTypeOnNoP2PChannel.Discard, true, action);
                }
            }
        } 
        #endregion

        #region Broadcast
        public void Broadcast(string groupID, int broadcastType, byte[] broadcastContent, ActionTypeOnChannelIsBusy action, BroadcastChannelMode broadcastChannelMode)
        {
            if (broadcastChannelMode == BroadcastChannelMode.AllTransferByServer)
            {
                this.BroadcastByServer(groupID, broadcastType, broadcastContent, action);
                return;
            }

            if (broadcastChannelMode == BroadcastChannelMode.AllByP2PChannel)
            {
                this.BroadcastByP2PChannel(groupID, broadcastType, broadcastContent, action);
                return;
            }

            BroadcastContract contract = new BroadcastContract(this.currentUserID, groupID, broadcastType, broadcastContent, action);
            byte[] info = CompactPropertySerializer.Default.Serialize(contract);
            SortedArray<string> passiveGroupmates = this.SafeGetGroupMembers(groupID);
            if (passiveGroupmates == null)
            {
                return;
            }
            bool allP2P = true;
            foreach (string memberID in passiveGroupmates.GetAll())
            {
                if (memberID == this.currentUserID)
                {
                    continue;
                }

                if (this.rapidPassiveEngine.P2PController.IsP2PChannelExist(memberID))
                {
                    this.rapidPassiveEngine.CustomizeOutter.SendByP2PChannel(memberID, this.groupInfoTypes.Broadcast, info, ActionTypeOnNoP2PChannel.Discard, true, action);
                }
                else
                {
                    allP2P = false;
                }
            }

            if (!allP2P) //如果有一个在线用户没有P2P，则经过服务器中转。
            {
                this.rapidPassiveEngine.CustomizeOutter.Send(null, this.groupInfoTypes.Broadcast, info, true, action);
            }
        }

        public void Broadcast(string groupID, int broadcastType, byte[] broadcastContent, ActionTypeOnChannelIsBusy action)
        {
            this.Broadcast(groupID, broadcastType, broadcastContent, action, BroadcastChannelMode.AllTransferByServer);
        } 
        #endregion

        #region BroadcastBlob
        private int maxBlobID = 0;
        public void BroadcastBlob(string groupID, int broadcastType, byte[] blobInfo, int fragmentSize, BroadcastChannelMode broadcastChannelMode)
        {
            if (blobInfo == null || blobInfo.Length == 0)
            {
                return;
            }

            if (fragmentSize == 0)
            {
                throw new ArgumentException("Value of fragmentSize must be greater than 0.");
            }

            if (blobInfo.Length <= fragmentSize)
            {
                this.Broadcast(groupID, broadcastType, blobInfo, ActionTypeOnChannelIsBusy.Continue, broadcastChannelMode);
                return;
            }

            int messageType = broadcastChannelMode == BroadcastChannelMode.AllTransferByServer ? this.groupInfoTypes.BroadcastBlobByServer : this.groupInfoTypes.BroadcastBlob;
            int blobID = System.Threading.Interlocked.Increment(ref this.maxBlobID);
            int count = blobInfo.Length / fragmentSize;
            if (blobInfo.Length % fragmentSize > 0)
            {
                count += 1;
            }

            if (broadcastChannelMode == BroadcastChannelMode.AllTransferByServer)
            {
                #region AllTransferByServer
                for (int i = 0; i < count; i++)
                {
                    byte[] fragment = null;
                    if (i < count - 1)
                    {
                        fragment = new byte[fragmentSize];
                    }
                    else
                    {
                        fragment = new byte[blobInfo.Length - i * fragmentSize];
                    }
                    Buffer.BlockCopy(blobInfo, i * fragmentSize, fragment, 0, fragment.Length);
                    BlobFragmentContract contract = new BlobFragmentContract(this.currentUserID, blobID, broadcastType, i, fragment, i == count - 1, groupID);
                    byte[] info = CompactPropertySerializer.Default.Serialize(contract);
                    this.rapidPassiveEngine.CustomizeOutter.Send(null, messageType, info, true, ActionTypeOnChannelIsBusy.Continue);
                }
                #endregion
            }
            else if (broadcastChannelMode == BroadcastChannelMode.P2PChannelFirst)
            {
                #region P2PChannelFirst
                SortedArray<string> passiveGroupmates = this.SafeGetGroupMembers(groupID);
                if (passiveGroupmates == null)
                {
                    return;
                }

                for (int i = 0; i < count; i++)
                {
                    bool allP2P = true;
                    byte[] fragment = null;
                    if (i < count - 1)
                    {
                        fragment = new byte[fragmentSize];
                    }
                    else
                    {
                        fragment = new byte[blobInfo.Length - i * fragmentSize];
                    }
                    Buffer.BlockCopy(blobInfo, i * fragmentSize, fragment, 0, fragment.Length);
                    BlobFragmentContract contract = new BlobFragmentContract(this.currentUserID, blobID, broadcastType, i, fragment, i == count - 1, groupID);
                    byte[] info = CompactPropertySerializer.Default.Serialize(contract);

                    foreach (string memberID in passiveGroupmates.GetAll())
                    {
                        if (memberID == this.currentUserID)
                        {
                            continue;
                        }

                        if (this.rapidPassiveEngine.P2PController.IsP2PChannelExist(memberID))
                        {
                            this.rapidPassiveEngine.CustomizeOutter.SendByP2PChannel(memberID, messageType, info, ActionTypeOnNoP2PChannel.Discard, true, ActionTypeOnChannelIsBusy.Continue);
                        }
                        else
                        {
                            allP2P = false;
                        }
                    }

                    if (!allP2P) //如果有一个在线用户没有P2P，则经过服务器中转。
                    {
                        this.rapidPassiveEngine.CustomizeOutter.Send(null, messageType, info, true, ActionTypeOnChannelIsBusy.Continue);
                    }
                }
                #endregion
            }
            else //AllByP2PChannel
            {
                #region AllByP2PChannel
                SortedArray<string> passiveGroupmates = this.SafeGetGroupMembers(groupID);
                if (passiveGroupmates == null)
                {
                    return;
                }

                for (int i = 0; i < count; i++)
                {

                    byte[] fragment = null;
                    if (i < count - 1)
                    {
                        fragment = new byte[fragmentSize];
                    }
                    else
                    {
                        fragment = new byte[blobInfo.Length - i * fragmentSize];
                    }
                    Buffer.BlockCopy(blobInfo, i * fragmentSize, fragment, 0, fragment.Length);                   
                    BlobFragmentContract contract = new BlobFragmentContract(this.currentUserID, blobID, broadcastType, i, fragment, i == count - 1, groupID);
                    byte[] info = CompactPropertySerializer.Default.Serialize(contract);

                    foreach (string memberID in passiveGroupmates.GetAll())
                    {
                        if (memberID == this.currentUserID)
                        {
                            continue;
                        }

                        if (this.rapidPassiveEngine.P2PController.IsP2PChannelExist(memberID))
                        {
                            this.rapidPassiveEngine.CustomizeOutter.SendByP2PChannel(memberID, messageType, info, ActionTypeOnNoP2PChannel.Discard, true, ActionTypeOnChannelIsBusy.Continue);
                        }
                    }
                }
                #endregion
            }
        } 
        #endregion
    }

}
