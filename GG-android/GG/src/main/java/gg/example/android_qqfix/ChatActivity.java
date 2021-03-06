package gg.example.android_qqfix;

import android.app.Activity;
import android.app.TabActivity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.SpannableStringBuilder;
import android.text.style.ImageSpan;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnTouchListener;
import android.view.ViewGroup;
import android.view.ViewGroup.LayoutParams;
import android.view.Window;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.RelativeLayout;
import android.widget.TabHost;
import android.widget.TabHost.OnTabChangeListener;
import android.widget.TabHost.TabSpec;
import android.widget.TabWidget;
import android.widget.TextView;
import android.widget.Toast;

import com.oraycn.es.communicate.framework.model.SendingFileParas;
import com.oraycn.es.communicate.framework.model.TransferingProject;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import gg.dragon.persondata.ContactsInfo;
import gg.example.utils.FileUtils;
import gg.model.ChatContentContract;
import gg.model.ContractType;
import gg.model.FileInfo;
import gg.model.GGUser;
import gg.model.UserStatus;


public class ChatActivity extends TabActivity implements ChatApplication.ChatMessageListener, ChatApplication.FriendStatusChangedListener, OnClickListener {

    public final static int OTHER = 1;
    public final static int ME = 0;
    public final static int ActivityID = 0;
    public final static int HandlerOtherMessage = 2;
    public final static int FriendStatusChanged = 3;
    public final static int DisplayFileStatus = 4;
    public final static int HiddenFileStatus = 5;
    public static Handler chatHandler = null;
    public static String currentTabTag = "face";
    public TabSpec tabSpecFaceHistory, tabSpecFace;
    protected ListView chatListView = null;
    protected Button chatSendButton = null;
    protected EditText editText = null;
    protected TextView chatName = null;
    protected ImageButton chatBottomLook = null;
    protected ImageButton getChatBottomAdd = null;
    protected RelativeLayout faceLayout = null;
    protected TabHost tabHost = null;
    protected TabWidget tabWidget = null;
    protected MyChatAdapter adapter = null;
    protected View tabFaceHistory = null, tabFace = null;
    protected ImageView tabFaceHistoryImage = null, tabFaceImage = null;
    int[] faceId = {R.drawable.f_static_018, R.drawable.f_static_000, R.drawable.f_static_001, R.drawable.f_static_002, R.drawable.f_static_019, R.drawable.f_static_003
            , R.drawable.f_static_004, R.drawable.f_static_005, R.drawable.f_static_006, R.drawable.f_static_009, R.drawable.f_static_010, R.drawable.f_static_013, R.drawable.f_static_017, R.drawable.f_static_011
            , R.drawable.f_static_012, R.drawable.f_static_014, R.drawable.f_static_015};
    String[] faceName = {"\\微笑", "\\呲牙", "\\淘气", "\\流汗", "\\色色", "\\偷笑", "\\再见", "\\敲打", "\\擦汗", "\\流泪", "\\掉泪", "\\发狂", "\\菜刀", "\\小声", "\\炫酷",
            "\\委屈", "\\便便", "\\害羞"};
    HashMap<String, Integer> faceMap = null;
    ArrayList<HashMap<String, Object>> chatList = null;
    String[] from = {"image", "text"};
    int[] to = {R.id.chatlist_image_me, R.id.chatlist_text_me, R.id.chatlist_image_other, R.id.chatlist_text_other};
    int[] layout = {R.layout.chat_listitem_me, R.layout.chat_listitem_other};
    //String userQQ = null;
    private boolean expanded = false;
    private ChatApplication app;
    private String TalkingUserID;
    private List<String> faceList = null;
    private LinearLayout title = null;
    //private Filehandler filehandler = new Filehandler();
    private ListView fileMsgListView;
    private FileMessageAdapter fileAdapter;
    private Map<String, Integer> indexMap = new HashMap<String, Integer>();

    @Override
    public void ChatMessageReceived(String sourceUserID, ChatContentContract chatContent) {

        String content = this.GetChatString(chatContent);

        addTextToList(content, OTHER);
    }

    @Override
    public void FriendStatusChanged(String sourceUserID, UserStatus status) {

        if (sourceUserID.equals(this.TalkingUserID)) {
            Message message = new Message();
            message.what = FriendStatusChanged;
            message.obj = app.getMyFriendByID(sourceUserID).getName() + "(" + UserStatus.GetStatusName(status) + ")";
            chatHandler.sendMessage(message);
        }
    }

    @Override
    public void onClick(View v) {
        FileInfo info = (FileInfo) v.getTag();
        if (v.getId() == R.id.file_accept) {
            String projectID = info.getProjectID();
            indexMap.put(projectID, fileMsgListView.getCount());
            app.getEngine().getFileOutter()
                    .beginReceiveFile(projectID, "/sdcard/");
        } else {
            String projectID = info.getProjectID();
            app.getEngine().getFileOutter()
                    .cancelTransfering(projectID, "拒绝传输");
            Message message = new Message();
            message.what = HiddenFileStatus;
            chatHandler.sendMessage(message);
            app.showMessage("你已取消文件传输！");
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // TODO Auto-generated method stub
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.activity_chat);
        chatHandler = new MyChatHandler(Looper.myLooper());
        title = (LinearLayout) findViewById(R.id.chat_title);
        fileMsgListView = (ListView) findViewById(R.id.fileMessageList);
        fileAdapter = new FileMessageAdapter(this, this);
        fileMsgListView.setAdapter(fileAdapter);

        faceMap = new HashMap<String, Integer>();
        chatList = new ArrayList<HashMap<String, Object>>();
        app = (ChatApplication) getApplication();

        app.getEngine().getFileOutter().setFileEventListener(new FileEventListener());
        TalkingUserID = getIntent().getStringExtra("TalkingUserID");
        faceList = Arrays.asList(faceName);
        AddChatEvent();

        List<ChatContentContract> list = app.getAllChatInfoOfUser(TalkingUserID);
        if (list != null) {
            for (ChatContentContract chatContentContract : list) {
                addTextToList(this.GetChatString(chatContentContract), OTHER);
            }
        }

        chatSendButton = (Button) findViewById(R.id.chat_bottom_sendbutton);
        chatName = (TextView) findViewById(R.id.chat_contact_name);
        GGUser ou = app.getMyFriendByID(TalkingUserID);
        UserStatus status = UserStatus.OffLine;
        if (ou != null) {
            status = ou.getUserStatus();
        }
        chatName.setText(getIntent().getStringExtra("TalkingUserName") + "(" + UserStatus.GetStatusName(status) + ")");
        editText = (EditText) findViewById(R.id.chat_bottom_edittext);
        chatListView = (ListView) findViewById(R.id.chat_list);
        tabWidget = (TabWidget) findViewById(android.R.id.tabs);
        tabHost = (TabHost) findViewById(android.R.id.tabhost);

        chatBottomLook = (ImageButton) findViewById(R.id.chat_bottom_look);
        getChatBottomAdd = ((ImageButton) findViewById(R.id.chat_bottom_add));
        faceLayout = (RelativeLayout) findViewById(R.id.faceLayout);

        getChatBottomAdd.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                showFileChooser();
            }
        });

        /**
         * 添加选项卡
         */
        tabSpecFaceHistory = tabHost.newTabSpec("faceHistory");
        tabFaceHistory = LayoutInflater.from(this).inflate(R.layout.tabwidget_image_disselected, null);
        tabFaceHistoryImage = (ImageView) tabFaceHistory.findViewById(R.id.tabImage_disselected);
        tabFaceHistoryImage.setImageResource(R.drawable.face_history_disselected);
        tabSpecFaceHistory.setIndicator(tabFaceHistory);
        Intent intent1 = new Intent();
        intent1.setClass(ChatActivity.this, FaceHistoryActivity.class);
        tabSpecFaceHistory.setContent(intent1);
        tabHost.addTab(tabSpecFaceHistory);

        tabSpecFace = tabHost.newTabSpec("face");
        tabFace = LayoutInflater.from(this).inflate(R.layout.tabwidget_image_selected, null);
        tabFaceImage = (ImageView) tabFace.findViewById(R.id.tabImage_selected);
        tabFaceImage.setImageResource(R.drawable.face_look_selected);
        tabSpecFace.setIndicator(tabFace);
        Intent intent2 = new Intent();
        intent2.setClass(ChatActivity.this, MyFaceActivity.class);
        tabSpecFace.setContent(intent2);
        tabHost.addTab(tabSpecFace);

        tabHost.setCurrentTabByTag("face");
        tabHost.setOnTabChangedListener(new OnTabChangeListener() {

            @Override
            public void onTabChanged(String tabId) {
                // TODO Auto-generated method stub
                //	System.out.println("current Selected Tab "+tabId);
                currentTabTag = tabId;
                if (tabId.equals("face")) {
                    tabFace.setBackgroundResource(R.drawable.tabwidget_selected);
                    tabFaceImage.setImageResource(R.drawable.face_look_selected);
                    tabSpecFace.setIndicator(tabFace);

                    tabFaceHistory.setBackgroundResource(R.drawable.tab_widget_disselected);
                    tabFaceHistoryImage.setImageResource(R.drawable.face_history_disselected);
                    tabSpecFaceHistory.setIndicator(tabFaceHistory);
                } else if (tabId.equals("faceHistory")) {
                    tabFace.setBackgroundResource(R.drawable.tabwidget_disselected);
                    tabFaceImage.setImageResource(R.drawable.face_look_disselected);
                    tabSpecFace.setIndicator(tabFace);

                    tabFaceHistory.setBackgroundResource(R.drawable.tabwidget_selected);
                    tabFaceHistoryImage.setImageResource(R.drawable.face_history_selected);
                    tabSpecFaceHistory.setIndicator(tabFaceHistory);
                }
            }
        });


        chatBottomLook.setOnClickListener(new OnClickListener() {

            @Override
            public void onClick(View v) {
                // TODO Auto-generated method stub
                if (expanded) {
                    setFaceLayoutExpandState(false);
                    expanded = false;
                    InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                    imm.toggleSoftInput(0, InputMethodManager.HIDE_NOT_ALWAYS);

                    /**height不设为0是因为，希望可以使再次打开时viewFlipper已经初始化为第一页 避免
                     *再次打开ViewFlipper时画面在动的结果,
                     *为了避免因为1dip的高度产生一个白缝，所以这里在ViewFlipper所在的RelativeLayout
                     *最上面添加了一个1dip高的黑色色块
                     */
                } else {

                    setFaceLayoutExpandState(true);
                    expanded = true;
                }
            }

        });

        /**EditText从未获得焦点到首次获得焦点时不会调用OnClickListener方法，所以应该改成OnTouchListener
         * 从而保证点EditText第一下就能够把表情界面关闭
         editText.setOnClickListener(new OnClickListener(){

        @Override public void onClick(View v) {
        // TODO Auto-generated method stub
        ViewGroup.LayoutParams params=viewFlipper.getLayoutParams();
        params.height=0;
        viewFlipper.setLayoutParams(params);
        expanded=false;
        }

        });
         **/
        editText.setOnTouchListener(new OnTouchListener() {

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                // TODO Auto-generated method stub
                if (expanded) {

                    setFaceLayoutExpandState(false);
                    expanded = false;
                }
                return false;
            }
        });
        adapter = new MyChatAdapter(this, chatList, layout, from, to);
        chatSendButton.setOnClickListener(new OnClickListener() {

            @Override
            public void onClick(View v) {
                // TODO Auto-generated method stub
                String myWord = null;

                myWord = (editText.getText() + "").toString();
                if (myWord.length() == 0)
                    return;
                editText.setText("");
                addTextToList(myWord, ME);

                ChatContentContract chatContent = GetChatContent(myWord);

                byte[] info = null;
                try {
                    info = chatContent.serialize();
                } catch (Exception ee) {

                }
                app.getEngine().sendMessage(null, ContractType.CHAT.getType(), info, TalkingUserID);
                /**
                 * 更新数据列表，并且通过setSelection方法使ListView始终滚动在最底端
                 */
                adapter.notifyDataSetChanged();
                chatListView.setSelection(chatList.size() - 1);

            }
        });

        chatListView.setAdapter(adapter);

        chatListView.setOnItemClickListener(new OnItemClickListener() {

            @Override
            public void onItemClick(AdapterView<?> arg0, View arg1, int arg2,
                                    long arg3) {
                // TODO Auto-generated method stub
                setFaceLayoutExpandState(false);
                ((InputMethodManager) ChatActivity.this.getSystemService(INPUT_METHOD_SERVICE)).
                        hideSoftInputFromWindow(ChatActivity.this.getCurrentFocus().getWindowToken(), InputMethodManager.HIDE_NOT_ALWAYS);
                expanded = false;
            }
        });

        /**
         * 为表情Map添加数据
         */
        for (int i = 0; i < faceId.length; i++) {
            faceMap.put(faceName[i], faceId[i]);
        }
    }

    /**
     * 打开或者关闭软键盘，之前若打开，调用该方法后关闭；之前若关闭，调用该方法后打开
     */
    private void setSoftInputState() {
        ((InputMethodManager) ChatActivity.this.getSystemService(INPUT_METHOD_SERVICE)).toggleSoftInput(0, InputMethodManager.HIDE_NOT_ALWAYS);
    }

    private void setFaceLayoutExpandState(boolean isexpand) {
        if (isexpand == false) {

            ViewGroup.LayoutParams params = faceLayout.getLayoutParams();
            params.height = 1;
            faceLayout.setLayoutParams(params);

            chatBottomLook.setBackgroundResource(R.drawable.chat_bottom_look);
            Message msg = new Message();
            msg.what = this.ActivityID;
            msg.obj = "collapse";
            if (MyFaceActivity.faceHandler != null)
                MyFaceActivity.faceHandler.sendMessage(msg);

            Message msg2 = new Message();
            msg2.what = this.ActivityID;
            msg2.obj = "collapse";
            if (FaceHistoryActivity.faceHistoryHandler != null)
                FaceHistoryActivity.faceHistoryHandler.sendMessage(msg2);

            chatListView.setSelection(chatList.size() - 1);//使会话列表自动滑动到最低端
        } else {

            ((InputMethodManager) getSystemService(INPUT_METHOD_SERVICE)).hideSoftInputFromWindow
                    (ChatActivity.this.getCurrentFocus().getWindowToken(), InputMethodManager.HIDE_NOT_ALWAYS);
            ViewGroup.LayoutParams params = faceLayout.getLayoutParams();
            params.height = 185;
            //	faceLayout.setLayoutParams(new RelativeLayout.LayoutParams( ));
            RelativeLayout.LayoutParams relativeParams = new RelativeLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
            relativeParams.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM, RelativeLayout.TRUE);
            faceLayout.setLayoutParams(relativeParams);

            chatBottomLook.setBackgroundResource(R.drawable.chat_bottom_keyboard);
        }
    }

    private void setFaceText(TextView textView, String text) {
        SpannableString spanStr = parseString(text);
        textView.setText(spanStr);
    }

    private void setFace(SpannableStringBuilder spb, String faceName) {
        Integer faceId = faceMap.get(faceName);
        if (faceId != null) {
            Bitmap bitmap = BitmapFactory.decodeResource(getResources(), faceId);
            bitmap = Bitmap.createScaledBitmap(bitmap, 30, 30, true);
            ImageSpan imageSpan = new ImageSpan(this, bitmap);
            SpannableString spanStr = new SpannableString(faceName);
            spanStr.setSpan(imageSpan, 0, faceName.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            spb.append(spanStr);
        } else {
            spb.append(faceName);
        }

    }

    private SpannableString parseString(String inputStr) {
        SpannableStringBuilder spb = new SpannableStringBuilder();
        Pattern mPattern = Pattern.compile("\\\\..");
        Matcher mMatcher = mPattern.matcher(inputStr);
        String tempStr = inputStr;

        while (mMatcher.find()) {
            int start = mMatcher.start();
            int end = mMatcher.end();
            spb.append(tempStr.substring(0, start));
            String faceName = mMatcher.group();
            setFace(spb, faceName);
            tempStr = tempStr.substring(end, tempStr.length());
            /**
             * 更新查找的字符串
             */
            mMatcher.reset(tempStr);
        }
        spb.append(tempStr);
        return new SpannableString(spb);
    }

    protected void addTextToList(String text, int who) {
        HashMap<String, Object> map = new HashMap<String, Object>();
        map.put("person", who);
        map.put("image", who == ME ? ContactsInfo.headImgMap.get(app.getMyUserInfo().getHeadImageIndex()) :
                ContactsInfo.headImgMap.get(app.getMyFriendByID(TalkingUserID).getHeadImageIndex()));
        map.put("text", text);
        chatList.add(map);
        if (who == OTHER) {
            Message message = new Message();
            message.what = HandlerOtherMessage;
            chatHandler.sendMessage(message);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        //((CustomizeHandler) app.getEngine().getCustomizeHandler()).removeInformationListener(ContractType.CHAT.getType(), this);
        if (TalkingUserID != null) {
            app.RemoveChatMessageListener(TalkingUserID, this);
        }
        app.RemoveFriendStatusChangedListener(this);
    }

    private void AddChatEvent() {
        app.AddChatMessageListener(TalkingUserID, this);
        app.AddFriendStatusChangedListener(this);
    }

    /**
     * 包装聊天对象
     *
     * @param chatStr
     * @return
     */
    private ChatContentContract GetChatContent(String chatStr) {
        StringBuilder stringBuilder = new StringBuilder(chatStr);
        ChatContentContract chatContent = new ChatContentContract();
        Map<Integer, Integer> hashMap = new HashMap<Integer, Integer>();
        for (int i = 0; i < stringBuilder.length(); i++) {
            if (stringBuilder.charAt(i) == '\\') {
                String tempStr = stringBuilder.substring(i, i + 3);
                if (faceList.contains(tempStr)) {
                    hashMap.put(i, faceList.indexOf(tempStr));
                    stringBuilder.replace(i, i + 3, " ");
                }
            }
        }

        chatContent.setEmotionMap(hashMap);

        chatContent.setText(stringBuilder.toString());

        return chatContent;
    }

    /**
     * 输出聊天内容
     *
     * @param content
     * @return
     */
    private String GetChatString(ChatContentContract content) {
        String contentStr = content.getText();
        if (content.getEmotionMap().size() > 0) {
            StringBuilder newContent = new StringBuilder(contentStr);
            int i = 0;

            for (Map.Entry<Integer, Integer> entry : content.getEmotionMap().entrySet()) {

                newContent.insert(entry.getKey() + i, faceName[entry.getValue()]);
                i = i + 3;
            }
            contentStr = newContent.toString();
        }

        return contentStr;
    }

    private void showFileChooser() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("*/*");
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        try {
            startActivityForResult(Intent.createChooser(intent, "请选择一个要上传的文件"), 1);
        } catch (android.content.ActivityNotFoundException ex) {
            Toast.makeText(this, "请安装文件管理器", Toast.LENGTH_SHORT).show();
        }
    }

    private void sendImage(String filePath) {

        try {
            Bitmap rawBitmap = BitmapFactory.decodeFile(filePath, null);

            ByteArrayOutputStream stream = new ByteArrayOutputStream();

            rawBitmap.compress(Bitmap.CompressFormat.PNG, 100, stream);

            byte[] byteArray = stream.toByteArray();

            stream.close();

            app.getEngine().getCustomizeOutter().sendBlob(this.TalkingUserID, ContractType.CHATPIC.getType(), byteArray, 1024);
        } catch (Exception ee) {
            ee.printStackTrace();
        }
        //stream.close();


    }

    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        // TODO Auto-generated method stub
        if (resultCode == Activity.RESULT_OK) {
            // Get the Uri of the selected file
            Uri uri = data.getData();
            String url = FileUtils.getPath(ChatActivity.this, uri);

            Log.e("info", "select file is :" + url);
            SendingFileParas paras = new SendingFileParas((1024 * 200), 0);
            String projectID = app.getEngine().getFileOutter()
                    .beginSendFile(TalkingUserID, url, paras, "");

            FileInfo attach = new FileInfo();
//			attach.setComment("");
            attach.setFileName(url.substring(url.lastIndexOf("/") + 1));
            attach.setProjectID(projectID);
//			attach.setResumedFileItem(resumedFileItem);
            attach.setSenderID(app.getEngine().getCurrentUserID());
            Message message = new Message();
            message.what = DisplayFileStatus;
            chatHandler.sendMessage(message);
            indexMap.put(projectID, fileMsgListView.getCount() + 1);
            fileAdapter.fileList.add(attach);
            fileMsgListView.post(new Runnable() {
                @Override
                public void run() {
                    fileMsgListView.setSelection(fileAdapter.getCount() - 1);
                    fileAdapter.notifyDataSetChanged();
                }
            });
        }

        super.onActivityResult(requestCode, resultCode, data);
    }

    public class MyChatHandler extends Handler {
        public MyChatHandler(Looper looper) {
            super(looper);
        }

        @Override
        public void handleMessage(Message msg) {
            // TODO Auto-generated method stub
            switch (msg.what) {
                case MyFaceActivity.ActivityId:
                    if (msg.arg1 == 0) {            //添加表情字符串
                        editText.append(msg.obj.toString());
                    }
                    break;
                case ChatActivity.HandlerOtherMessage:
                    adapter.notifyDataSetChanged();
                    chatListView.setSelection(chatList.size() - 1);
                    break;
                case ChatActivity.FriendStatusChanged:
                    chatName.setText(msg.obj.toString());
                    break;
                case ChatActivity.DisplayFileStatus:
                    RelativeLayout.LayoutParams linearParam = (RelativeLayout.LayoutParams) title.getLayoutParams();
                    linearParam.height += 180;
                    title.setLayoutParams(linearParam);
                    fileMsgListView.setVisibility(View.VISIBLE);

                    new Handler().postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            fileMsgListView.setSelection(fileAdapter.getCount() - 1);
                        }
                    }, 200);

                    break;
                case ChatActivity.HiddenFileStatus:
                    RelativeLayout.LayoutParams linearParams = (RelativeLayout.LayoutParams) title.getLayoutParams();
                    linearParams.height -= 180;
                    title.setLayoutParams(linearParams);
                    fileMsgListView.setVisibility(View.GONE);
                    break;
            }
        }
    }

    private class MyChatAdapter extends BaseAdapter {

        Context context = null;
        ArrayList<HashMap<String, Object>> chatList = null;
        int[] layout;
        String[] from;
        int[] to;


        public MyChatAdapter(Context context,
                             ArrayList<HashMap<String, Object>> chatList, int[] layout,
                             String[] from, int[] to) {
            super();
            this.context = context;
            this.chatList = chatList;
            this.layout = layout;
            this.from = from;
            this.to = to;
        }

        @Override
        public int getCount() {
            // TODO Auto-generated method stub
            return chatList.size();
        }

        @Override
        public Object getItem(int arg0) {
            // TODO Auto-generated method stub
            return null;
        }

        @Override
        public long getItemId(int position) {
            // TODO Auto-generated method stub
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            // TODO Auto-generated method stub
            ViewHolder holder = null;
            int who = (Integer) chatList.get(position).get("person");

            convertView = LayoutInflater.from(context).inflate(
                    layout[who == ME ? 0 : 1], null);
            holder = new ViewHolder();
            holder.imageView = (ImageView) convertView.findViewById(to[who * 2 + 0]);
            holder.textView = (TextView) convertView.findViewById(to[who * 2 + 1]);


            holder.imageView.setBackgroundResource((Integer) chatList.get(position).get(from[0]));
            setFaceText(holder.textView, chatList.get(position).get(from[1]).toString());
            return convertView;
        }

        class ViewHolder {
            public ImageView imageView = null;
            public TextView textView = null;
        }
    }

     class FileEventListener implements com.oraycn.es.communicate.framework.FileEventListener {
        @Override
        public void fileRequestReceived(TransferingProject file) {
            Message message = new Message();
            message.what = DisplayFileStatus;
            chatHandler.sendMessage(message);

            FileInfo info = new FileInfo();
            info.setComment(file.getComment());
            info.setFileName(file.getFileName());
            info.setProjectID(file.getProjectId());
            //info.setResumedFileItem(resumedFileItem);
            info.setSenderID(file.getSender());
            info.setTotalSize(file.getFileSize());

            fileAdapter.fileList.add(info);
            fileMsgListView.post(new Runnable() {
                @Override
                public void run() {
                    fileMsgListView.setSelection(fileAdapter.getCount() - 1);
                    fileAdapter.notifyDataSetChanged();
                }
            });
        }

        @Override
        public void fileResponseReceived(TransferingProject file, boolean accept) {

        }

        @Override
        public void fileTransStarted(TransferingProject project) {
            Message message = new Message();
            message.what = DisplayFileStatus;
            chatHandler.sendMessage(message);
            app.showMessage("开始传送文件" + project.getFileName());
        }

        @Override
        public void fileResumedTransStarted(TransferingProject project) {

        }

        @Override
        public void fileSendedProgress(String projectId, long totlaSize, long transfered) {

            final int currentTransfered = (int) ((float) ((float) transfered * 100 / (float) totlaSize));
            if (indexMap.get(projectId) != null) {
                int index = indexMap.get(projectId) - 1;
                final FileMessageAdapter.MessageHold hold = (FileMessageAdapter.MessageHold) fileMsgListView.getChildAt(index).getTag();
                fileMsgListView.post(new Runnable() {
                    @Override
                    public void run() {
                        hold.bar.setProgress(currentTransfered);
                    }
                });
            }
        }

        @Override
        public void fileTransDisruptted(String projectId, FileEventListener.FileTransDisrupttedType
        disrupttedType, String cause) {
            app.showMessage("文件["+ projectId+  "]传输中断:" + cause);
        }

        @Override
        public void fileTransCompleted(String projectId) {
                        Message message = new Message();
            message.what = HiddenFileStatus;
            chatHandler.sendMessage(message);
            app.showMessage("文件传输完成！");
        }
    }
}
