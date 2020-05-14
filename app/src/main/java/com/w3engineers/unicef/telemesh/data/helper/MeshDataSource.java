package com.w3engineers.unicef.telemesh.data.helper;

import android.annotation.SuppressLint;
import android.content.Context;
import android.support.annotation.NonNull;
import android.text.TextUtils;

import com.google.gson.Gson;
import com.w3engineers.ext.strom.util.helper.data.local.SharedPref;
import com.w3engineers.mesh.util.lib.mesh.HandlerUtil;
import com.w3engineers.models.ContentMetaInfo;
import com.w3engineers.unicef.TeleMeshApplication;
import com.w3engineers.unicef.telemesh.data.broadcast.BroadcastManager;
import com.w3engineers.unicef.telemesh.data.broadcast.SendDataTask;
import com.w3engineers.unicef.telemesh.data.helper.constants.Constants;
import com.w3engineers.unicef.telemesh.data.local.usertable.UserModel;
import com.w3engineers.unicef.util.helper.BulletinTimeScheduler;
import com.w3engineers.unicef.util.helper.ContentUtil;
import com.w3engineers.unicef.util.helper.TextToImageHelper;
import com.w3engineers.unicef.util.helper.ViperUtil;
import com.w3engineers.unicef.util.helper.model.ViperContentData;
import com.w3engineers.unicef.util.helper.model.ViperData;

import java.util.HashMap;
import java.util.List;

import timber.log.Timber;

/*
 * ============================================================================
 * Copyright (C) 2019 W3 Engineers Ltd - All Rights Reserved.
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 * ============================================================================
 */
public class MeshDataSource extends ViperUtil {

    @SuppressLint("StaticFieldLeak")
    private static MeshDataSource rightMeshDataSource;
    public static boolean isPrepared = false;
    private BroadcastManager broadcastManager;

    private HashMap<String, ContentReceiveModel> contentReceiveModelHashMap = new HashMap<>();
    private HashMap<String, ContentSendModel> contentSendModelHashMap = new HashMap<>();

    private MeshDataSource(@NonNull UserModel userModel) {
        super(userModel);
        broadcastManager = BroadcastManager.getInstance();
    }


    @NonNull
    static MeshDataSource getRmDataSource() {
        if (rightMeshDataSource == null) {
            Context context = TeleMeshApplication.getContext();

            SharedPref sharedPref = SharedPref.getSharedPref(context);

            UserModel userModel = new UserModel()
                    .setName(sharedPref.read(Constants.preferenceKey.USER_NAME))
                    .setImage(sharedPref.readInt(Constants.preferenceKey.IMAGE_INDEX))
                    .setTime(sharedPref.readLong(Constants.preferenceKey.MY_REGISTRATION_TIME));

            rightMeshDataSource = new MeshDataSource(userModel);
        }
        return rightMeshDataSource;
    }

    @Override
    protected void onMesh(String myMeshId) {
        meshInited(myMeshId);
        RmDataHelper.getInstance().meshInitiated();
    }

    @Override
    protected void onMeshPrepared(String myWalletAddress) {
        meshInited(myWalletAddress);
    }

    @Override
    protected void offMesh() {
        RmDataHelper.getInstance().destroyMeshService();
    }

    private void meshInited(String meshId) {
        //when RM will be on then prepare this observer to listen the outgoing messages

        SharedPref.getSharedPref(TeleMeshApplication.getContext()).write(Constants.preferenceKey.MY_USER_ID, meshId);

        if (!isPrepared) {
            RmDataHelper.getInstance().prepareDataObserver();
            TextToImageHelper.writeWalletAddressToImage(meshId);
            isPrepared = true;
        }

        Constants.IsMeshInit = true;

        BulletinTimeScheduler.getInstance().checkAppUpdate();
    }

    /*public void stopAllServices() {
        // TODO stop service during mode change from data plan mode
    }*/

    /**
     * During send data to peer
     *
     * @param dataModel -> A generic data model which contains userData, type and peerId
     * @return return the send message id
     */
    public void DataSend(@NonNull DataModel dataModel, @NonNull String receiverId, boolean isNotificationEnable) {

        if (!TextUtils.isEmpty(receiverId)) {
            dataModel.setUserId(receiverId);

            ViperData viperData = new ViperData();
            viperData.rawData = dataModel.getRawData();
            viperData.dataType = dataModel.getDataType();
            viperData.isNotificationEnable = isNotificationEnable;

            broadcastManager.addBroadCastMessage(getMeshDataTask(viperData, receiverId));
        }
    }

    public void DataSend(@NonNull DataModel dataModel, @NonNull List<String> receiverIds, boolean isNotificationEnable) {
        for (String receiverId : receiverIds) {
            DataSend(dataModel, receiverId, isNotificationEnable);
        }
    }

    private SendDataTask getMeshDataTask(ViperData viperData, String receiverId) {
        return new SendDataTask().setPeerId(receiverId).setMeshData(viperData).setBaseRmDataSource(this);
    }

    public void ContentDataSend(ContentModel contentModel, boolean notificationEnable) {
        String receiverId = contentModel.getUserId();

        if (!TextUtils.isEmpty(receiverId)) {
            ViperContentData viperContentData = new ViperContentData();
            viperContentData.dataType = contentModel.getContentDataType();
            viperContentData.contentModel = contentModel;
            viperContentData.isNotificationEnable = notificationEnable;

            broadcastManager.addBroadCastMessage(getMeshContentDataTask(receiverId, viperContentData));
        }
    }

    private SendDataTask getMeshContentDataTask(String receiverId, ViperContentData viperContentData) {
        return new SendDataTask().setPeerId(receiverId).setViperContentData(viperContentData)
                .setBaseRmDataSource(this);
    }

    /**
     * During receive a peer this time onPeer api is execute
     *
     * @param peerData -> Got a peer data (profile information)
     */
    protected void peerAdd(String peerId, byte[] peerData) {

        try {

            if (!TextUtils.isEmpty(peerId)) {
                String userString = new String(peerData);
                UserModel userModel = new Gson().fromJson(userString, UserModel.class);

                if (userModel != null) {
                    userModel.setUserId(peerId);
                    HandlerUtil.postBackground(() -> RmDataHelper.getInstance().userAdd(userModel));
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    protected void peerAdd(String peerId, UserModel userModel) {

        if (!TextUtils.isEmpty(peerId)) {
            if (userModel != null) {
                userModel.setUserId(peerId);
                HandlerUtil.postBackground(() -> RmDataHelper.getInstance().userAdd(userModel));
            }
        }
    }

    /**
     * When a peer is gone or switched the another network
     * this time onPeerGone api is executed
     *
     * @param peerId - > It contains the peer id which is currently inactive in mesh
     */
    @Override
    protected void peerRemove(@NonNull String peerId) {

        if (!TextUtils.isEmpty(peerId)) {
            HandlerUtil.postBackground(() -> RmDataHelper.getInstance().userLeave(peerId));
        }
    }

    /**
     * This api execute during we receive data from network
     *
     * @param viperData -> Contains data and peer info also
     */
    @Override
    protected void onData(@NonNull String peerId, ViperData viperData) {

        if (!TextUtils.isEmpty(peerId)) {
            DataModel dataModel = new DataModel()
                    .setUserId(peerId)
                    .setRawData(viperData.rawData)
                    .setDataType(viperData.dataType);

            HandlerUtil.postBackground(() -> RmDataHelper.getInstance().dataReceive(dataModel, true));
        }
    }

    /**
     * The sending data status is success this time we got a success ack using this api
     *
     * @param messageId -> Contains the success data id and user id
     */
    @Override
    protected void onAck(@NonNull String messageId, int status) {
        DataModel rmDataModel = new DataModel()
                .setDataTransferId(messageId)
                .setAckSuccess(true);

        HandlerUtil.postBackground(() -> RmDataHelper.getInstance().ackReceive(rmDataModel, status));
    }

    @Override
    protected boolean isNodeAvailable(String nodeId, int userActiveStatus) {
        return RmDataHelper.getInstance().userExistedOperation(nodeId, userActiveStatus);
    }

    protected void contentDataSend(String contentId, ContentModel contentModel) {

        if (contentModel.isRequestFromReceiver()) {
            return;
        }

        if (!TextUtils.isEmpty(contentId)) {

            if (contentModel.isThumbSend()) {

                ContentSendModel contentSendModel = contentSendModelHashMap.get(contentId);

                if (contentSendModel == null) {
                    contentSendModel = new ContentSendModel();
                }

                contentSendModel.contentId = contentId;
                contentSendModel.messageId = contentModel.getMessageId();
                contentSendModel.userId = contentModel.getUserId();
                contentSendModel.successStatus = true;
                contentSendModel.contentDataType = contentModel.getContentDataType();

                HandlerUtil.postBackground(() -> RmDataHelper.getInstance()
                        .setMessageContentId(contentModel.getMessageId(), contentId,
                                contentModel.getContentPath()));
                contentSendModelHashMap.put(contentId, contentSendModel);
            }
        } else {
            contentModel.setAckStatus(Constants.MessageStatus.STATUS_FAILED);
            HandlerUtil.postBackground(() -> RmDataHelper.getInstance()
                    .contentReceive(contentModel, false));
        }
    }

    public void setProgressInfoInMap(ContentModel contentModel, boolean isReceived) {

        if (isReceived) {
            if (contentReceiveModelHashMap.get(contentModel.getContentId()) == null) {
                ContentMetaInfo contentMetaInfo = new ContentMetaInfo()
                        .setMessageId(contentModel.getMessageId())
                        .setContentType(contentModel.getContentDataType())
                        .setMessageType(contentModel.getMessageType());

                ContentReceiveModel contentReceiveModel = new ContentReceiveModel()
                        .setContentId(contentModel.getContentId())
                        .setContentPath(contentModel.getContentPath())
                        .setUserId(contentModel.getUserId())
                        .setContentMetaInfo(contentMetaInfo)
                        .setSuccessStatus(true);

                contentReceiveModelHashMap.put(contentModel.getContentId(), contentReceiveModel);
                contentSendModelHashMap.remove(contentModel.getContentId());
            }
        } else {
            if (contentSendModelHashMap.get(contentModel.getContentId()) == null) {
                ContentSendModel contentSendModel = new ContentSendModel();

                contentSendModel.contentId = contentModel.getContentId();
                contentSendModel.messageId = contentModel.getMessageId();
                contentSendModel.userId = contentModel.getUserId();
                contentSendModel.successStatus = true;
                contentSendModel.contentDataType = contentModel.getContentDataType();

                contentSendModelHashMap.put(contentModel.getContentId(), contentSendModel);
            }
        }
    }

    @Override
    protected void contentReceiveStart(String contentId, String contentPath, String userId, byte[] metaData) {

        Timber.tag("FileMessage").v(" Start id: %s", contentId);

        ContentReceiveModel contentReceiveModel = contentReceiveModelHashMap.get(contentId);

        ContentMetaInfo contentMetaInfo = null;

        if (metaData != null) {
            String contentMessageText = new String(metaData);
            contentMetaInfo = new Gson().fromJson(contentMessageText,
                    ContentMetaInfo.class);
        }

        if (contentReceiveModel == null) {
            contentReceiveModel = new ContentReceiveModel();
        }

        contentReceiveModel
                .setContentId(contentId)
                .setContentPath(contentPath)
                .setUserId(userId)
                .setContentMetaInfo(contentMetaInfo)
                .setSuccessStatus(true);

        if (contentMetaInfo != null) {

            ContentMetaInfo finalContentMetaInfo = contentMetaInfo;

            HandlerUtil.postBackground(() -> RmDataHelper.getInstance()
                    .updateMessageStatus(finalContentMetaInfo.getMessageId()));

            if (contentMetaInfo.getContentType() == Constants.DataType.CONTENT_MESSAGE) {
                HandlerUtil.postBackground(() -> {
                    RmDataHelper.getInstance().setMessageContentId(finalContentMetaInfo.getMessageId(),
                            contentId, contentPath);

                });
            }
        }
        contentReceiveModelHashMap.put(contentId, contentReceiveModel);
    }

    @Override
    protected void contentReceiveInProgress(String contentId, int progress) {
        if (progress > 100)
            progress = 100;
        Timber.tag("FileMessage").v(" Progress: %s", progress);
        ContentReceiveModel contentReceiveModel = contentReceiveModelHashMap.get(contentId);
        if (contentReceiveModel != null) {
            contentReceiveModel.setContentReceiveProgress(progress);
            contentReceiveModelHashMap.put(contentId, contentReceiveModel);
            ContentMetaInfo contentMetaInfo = contentReceiveModel.getContentMetaInfo();
            if (contentMetaInfo != null &&
                    contentMetaInfo.getContentType() == Constants.DataType.CONTENT_MESSAGE) {
                String messageId = contentMetaInfo.getMessageId();
                RmDataHelper.getInstance().setContentProgress(messageId, progress, contentId);
            }
            return;
        }

        ContentSendModel contentSendModel = contentSendModelHashMap.get(contentId);
        if (contentSendModel != null) {
            RmDataHelper.getInstance().setContentProgress(contentSendModel.messageId, progress, contentSendModel.contentId);
            contentSendModel.contentReceiveProgress = progress;

            contentSendModelHashMap.put(contentId, contentSendModel);
        } else {

            ContentModel contentModel = RmDataHelper.getInstance().setContentProgressByContentIdForSender(contentId, progress);

            if (contentModel != null) {
                contentSendModel = new ContentSendModel();

                contentSendModel.contentId = contentId;
                contentSendModel.messageId = contentModel.getMessageId();
                contentSendModel.userId = contentModel.getUserId();
                contentSendModel.successStatus = true;
                contentSendModel.contentDataType = contentModel.getContentDataType();

                contentSendModelHashMap.put(contentId, contentSendModel);
            }
        }
    }

    @Override
    protected void contentReceiveDone(String contentId, boolean contentStatus) {
        Timber.tag("FileMessage").v(" status: %s", contentStatus);
        ContentReceiveModel contentReceiveModel = contentReceiveModelHashMap.get(contentId);
        if (contentReceiveModel != null) {
            ContentMetaInfo contentMetaInfo = contentReceiveModel.getContentMetaInfo();

            String contentPath = null, thumbPath = null;

            if (contentStatus) {
                switch (contentMetaInfo.getContentType()) {
                    case Constants.DataType.CONTENT_MESSAGE:
                        contentPath = ContentUtil.getInstance().getCopiedFilePath(
                                contentReceiveModel.getContentPath(), false);
                        break;
                    case Constants.DataType.CONTENT_THUMB_MESSAGE:
                        thumbPath = ContentUtil.getInstance().getCopiedFilePath(
                                contentReceiveModel.getContentPath(), true);
                        break;
                }
            }

            ContentModel contentModel = new ContentModel()
                    .setMessageId(contentMetaInfo.getMessageId())
                    .setMessageType(contentMetaInfo.getMessageType())
                    .setContentPath(contentPath)
                    .setThumbPath(thumbPath)
                    .setContentDataType(contentMetaInfo.getContentType())
                    .setUserId(contentReceiveModel.getUserId())
                    .setReceiveSuccessStatus(contentStatus);

            HandlerUtil.postBackground(() -> RmDataHelper.getInstance()
                    .contentReceive(contentModel, true));

            contentReceiveModelHashMap.remove(contentId);
            return;
        }

        /*****************************Sender side calculation*******************************/
        ContentSendModel contentSendModel = contentSendModelHashMap.get(contentId);
        if (contentSendModel != null) {
            if (contentStatus) {
                ContentModel contentModel = new ContentModel()
                        .setMessageId(contentSendModel.messageId)
                        .setContentDataType(contentSendModel.contentDataType)
                        .setAckStatus(Constants.MessageStatus.STATUS_RECEIVED);

                HandlerUtil.postBackground(() -> RmDataHelper.getInstance()
                        .contentReceive(contentModel, false));

            } else {

                ContentModel contentModel = new ContentModel()
                        .setMessageId(contentSendModel.messageId)
                        .setContentDataType(contentSendModel.contentDataType)
                        .setAckStatus(Constants.MessageStatus.STATUS_FAILED);

                HandlerUtil.postBackground(() -> RmDataHelper.getInstance()
                        .contentReceive(contentModel, false));
            }
            contentSendModelHashMap.remove(contentId);
        }
    }

    @Override
    protected void pendingContents(ContentPendingModel contentPendingModel) {
        if (contentPendingModel.isIncoming()) {

            ContentMetaInfo contentMetaInfo = contentPendingModel.getContentMetaInfo();
            String filePath = contentPendingModel.getContentPath();
            String userAddress = contentPendingModel.getSenderId();

            String contentId = contentPendingModel.getContentId();
            int state = contentPendingModel.getState();
            int progress = contentPendingModel.getProgress();

            if (!TextUtils.isEmpty(filePath)) {
                // receive started from root
                if (contentMetaInfo != null) {

                    String contentPath = null, thumbPath = null;

                    switch (contentMetaInfo.getContentType()) {
                        case Constants.DataType.CONTENT_MESSAGE:
                            if (state == Constants.ServiceContentState.SUCCESS) {
                                contentPath = ContentUtil.getInstance()
                                        .getCopiedFilePath(filePath, false);
                            } else {
                                contentPath = filePath;
                            }
                            break;
                        case Constants.DataType.CONTENT_THUMB_MESSAGE:
                            if (state == Constants.ServiceContentState.SUCCESS) {
                                thumbPath = ContentUtil.getInstance().getCopiedFilePath(
                                        filePath, true);
                            } else {
                                thumbPath = filePath;
                            }
                            break;
                    }

                    prepareContentModel(contentMetaInfo.getMessageId(),
                            contentPath, thumbPath, userAddress, contentId,
                            contentMetaInfo.getMessageType(), progress,
                            contentMetaInfo.getContentType(),  state);
                } else {
                    prepareContentModel(contentId, progress, state);
                }
            } else {
                if (!TextUtils.isEmpty(contentId)) {
                    prepareContentModel(contentId, progress, state);
                }
            }
        } else {
            String contentId = contentPendingModel.getContentId();
            int state = contentPendingModel.getState();
            int progress = contentPendingModel.getProgress();
            String userId = contentPendingModel.getSenderId();

            if (!TextUtils.isEmpty(contentId)) {
                prepareSendContentModel(contentId, userId, progress, state);
            }
        }
    }

    private void prepareContentModel(String contentId, int progress, int status) {
        ContentModel contentModel = new ContentModel()
                .setContentId(contentId)
                .setProgress(progress)
                .setAckStatus(status);

        HandlerUtil.postBackground(() -> RmDataHelper.getInstance()
                .receiveIncomingContentInfo(contentModel));
    }

    private void prepareSendContentModel(String contentId, String userId, int progress, int status) {
        ContentModel contentModel = new ContentModel()
                .setContentId(contentId)
                .setProgress(progress)
                .setUserId(userId)
                .setAckStatus(status);

        HandlerUtil.postBackground(() -> RmDataHelper.getInstance()
                .sendOutgoingContentInfo(contentModel));
    }

    private void prepareContentModel(String messageId, String contentPath, String thumbPath,
                                             String userId, String contentId, int messageType,
                                             int progress, byte contentType, int status) {
        ContentModel contentModel = new ContentModel()
                .setMessageId(messageId)
                .setMessageType(messageType)
                .setContentPath(contentPath)
                .setThumbPath(thumbPath)
                .setContentDataType(contentType)
                .setUserId(userId)
                .setContentId(contentId)
                .setProgress(progress)
                .setAckStatus(status);

        HandlerUtil.postBackground(() -> RmDataHelper.getInstance()
                .receiveIncomingContentInfo(contentModel));
    }

    /*@Override
    protected void configSync(boolean isUpdate, ConfigurationCommand configurationCommand) {
        RmDataHelper.getInstance().syncConfigFileAndBroadcast(isUpdate, configurationCommand);
    }*/

    // TODO SSID_Change
    /*public void resetInstance() {
        rightMeshDataSource = null;
    }*/

    public void saveUpdateUserInfo() {

        Context context = TeleMeshApplication.getContext();

        SharedPref sharedPref = SharedPref.getSharedPref(context);

        UserModel userModel = new UserModel()
                .setName(sharedPref.read(Constants.preferenceKey.USER_NAME))
                .setImage(sharedPref.readInt(Constants.preferenceKey.IMAGE_INDEX))
                .setTime(sharedPref.readLong(Constants.preferenceKey.MY_REGISTRATION_TIME));

        saveUserInfo(userModel);

    }

    public void saveUpdateOtherUserInfo(String userAddress, String userName, int imageIndex) {
        UserModel userModel = new UserModel().setUserId(userAddress)
                .setName(userName).setImage(imageIndex);

        saveOtherUserInfo(userModel);
    }

    public void checkUserIsConnected(String userId) {
        checkUserConnectionStatus(userId);
    }

}
