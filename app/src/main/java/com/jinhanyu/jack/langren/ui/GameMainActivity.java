package com.jinhanyu.jack.langren.ui;


import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.drawable.AnimationDrawable;
import android.graphics.drawable.BitmapDrawable;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.AlertDialog;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.widget.Gallery;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.PopupWindow;
import android.widget.TextView;
import android.widget.Toast;

import com.facebook.drawee.view.SimpleDraweeView;
import com.jinhanyu.jack.langren.MainApplication;
import com.jinhanyu.jack.langren.Me;
import com.jinhanyu.jack.langren.R;
import com.jinhanyu.jack.langren.SoundEffectManager;
import com.jinhanyu.jack.langren.TickTimer;
import com.jinhanyu.jack.langren.VoiceManager;
import com.jinhanyu.jack.langren.adapter.GalleryAdapter;
import com.jinhanyu.jack.langren.adapter.GameDetailAdapter;
import com.jinhanyu.jack.langren.entity.GameRole;
import com.jinhanyu.jack.langren.entity.RoomInfo;
import com.jinhanyu.jack.langren.entity.UserInfo;
import com.jinhanyu.jack.langren.util.RoundBitmapUtils;
import com.jinhanyu.jack.langren.util.ScreenUtils;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

import io.socket.emitter.Emitter;


public class GameMainActivity extends CommonActivity implements View.OnClickListener {
    private Gallery gallery;
    private GalleryAdapter adapter;
    private ListView gameDetailListview;
    private GameDetailAdapter gameDetailAdapter;
    private ImageView gameRule, voiceLevel;
    private AnimationDrawable speakAnim;
    private TextView identification;

    private DrawerLayout drawerLayout;//侧滑（显示玩家详细信息）


    private SimpleDraweeView bigHead;

    private View game_bg;
    private TextView identification_label;
    private View bt_endSpeak;
    private TextView time_label,tv_game_hint;
    private TickTimer tickTimer;
    private View speak_time_label;
    private View bt_wolf_destroy;


    private VoiceManager voiceManager = VoiceManager.getInstance(MainApplication.socket);
    private PopupWindow popupWindow;


    private boolean isLeavingWords = false;
    private UserInfo currentUser = MainApplication.roomInfo.findMeInRoom();

    private void finishSpeak() {
        bt_endSpeak.setEnabled(false);
        bt_wolf_destroy.setEnabled(false);
        voiceManager.stopRecord();
        if (tickTimer != null)
            tickTimer.cancel();
        speak_time_label.setVisibility(View.INVISIBLE);
        tv_game_hint.setVisibility(View.VISIBLE);
    }

    private void clearTopActivities(){
        startActivity(new Intent(GameMainActivity.this, GameMainActivity.class));
    }

    @Override
    protected void prepareViews() {
        setContentView(R.layout.game_main);
        watchNetworkState();

        //杂项: 计时器相关、游戏背景、声音动画、身份、标记
        time_label = (TextView) findViewById(R.id.time_label);
        tv_game_hint = (TextView) findViewById(R.id.tv_game_hint);
        speak_time_label = findViewById(R.id.speak_time_label);
        game_bg = findViewById(R.id.game_bg);
        voiceLevel = (ImageView) findViewById(R.id.iv_playStage_voiceLevel);
        speakAnim = (AnimationDrawable) voiceLevel.getDrawable();
        identification = (TextView) findViewById(R.id.tv_playStage_identification);
        identification_label = (TextView) findViewById(R.id.identification_label);
        identification.setOnClickListener(this);

        //画廊
        gallery = (Gallery) findViewById(R.id.gallery_players_head);
        adapter = new GalleryAdapter(this, MainApplication.roomInfo.getUsers());
        gallery.setAdapter(adapter);
        gallery.setSelection(MainApplication.roomInfo.findMyIndexInRoom());

        //大头像
        bigHead = (SimpleDraweeView) findViewById(R.id.iv_playStage_bigHead);
        bigHead.setImageURI(MainApplication.roomInfo.findMeInRoom().getHead());

        //游戏规则
        gameRule = (ImageView) findViewById(R.id.iv_gameStage_gameRule);
        gameRule.setOnClickListener(this);
        View gameRuleBg = getLayoutInflater().inflate(R.layout.game_rule, null);
        BitmapDrawable bitmapDrawable = (BitmapDrawable) gameRuleBg.getBackground();
        gameRuleBg.setBackground(new BitmapDrawable(RoundBitmapUtils.getRoundedCornerBitmap(bitmapDrawable.getBitmap())));
        popupWindow = new PopupWindow(gameRuleBg, ScreenUtils.getScreenWidth(this) * 3 / 4, ScreenUtils.getScreenHeight(this) * 2 / 3);
        popupWindow.setFocusable(true);
        popupWindow.setTouchable(true);
        popupWindow.setOutsideTouchable(true);
        popupWindow.setBackgroundDrawable(new BitmapDrawable());


        //结束发言按钮
        bt_endSpeak = findViewById(R.id.bt_endSpeak);
        bt_endSpeak.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                finishSpeak();
                if(isLeavingWords){
                    MainApplication.socket.emit("leaveWordsFinished", MainApplication.roomInfo.getRoomId(),true);
                }else{
                    MainApplication.socket.emit("pass", MainApplication.roomInfo.getRoomId());
                }
            }
        });

        //自爆按钮
        bt_wolf_destroy = findViewById(R.id.bt_wolf_destroy);
        bt_wolf_destroy.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                AlertDialog.Builder dialog = new AlertDialog.Builder(GameMainActivity.this);
                dialog.setTitle("请做出您的选择：");
                dialog.setMessage("您确定要自爆吗?自爆可以终止发言阶段,直接进入天黑");
                dialog.setPositiveButton("确定", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        SoundEffectManager.play(R.raw.wolf_self_destruction);//狼人自爆音效
                        finishSpeak();
                        MainApplication.socket.emit("wolfDestroy",MainApplication.roomInfo.getRoomId(), Me.getUserId());
                    }
                });
                dialog.setNegativeButton("取消", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();
                    }
                });
                dialog.show();
            }
        });


        //侧边栏
        drawerLayout = (DrawerLayout) findViewById(R.id.drawer);
        drawerLayout.setDrawerListener(new DrawerLayout.DrawerListener() {
            private boolean hasRefreshed;
            @Override
            public void onDrawerSlide(View drawerView, float slideOffset) {
                  if(!hasRefreshed){
                      hasRefreshed = true;
                      gameDetailAdapter.notifyDataSetChanged();
                  }
            }

            @Override
            public void onDrawerOpened(View drawerView) {
                drawerView.setClickable(true);  //动态设置Clickbale可以解决穿透点击事件
            }

            @Override
            public void onDrawerClosed(View drawerView) {
                   hasRefreshed = false;
            }

            @Override
            public void onDrawerStateChanged(int newState) {

            }
        });
        gameDetailListview = (ListView) findViewById(R.id.gameDetail_listView);
        gameDetailAdapter = new GameDetailAdapter(this, MainApplication.roomInfo.getUsers());
        gameDetailListview.setAdapter(gameDetailAdapter);

    }

    protected void prepareSocket() {

        MainApplication.socket
                .on("start", new Emitter.Listener() {
                    @Override
                    public void call(Object... args) {
                        final int type = (int) args[0];
                        final UserInfo me =MainApplication.roomInfo.findMeInRoom();
                        me.getGameRole().setType(type);
                        identification_label.post(new Runnable() {
                            @Override
                            public void run() {
                                identification_label.setText("您的身份是: " + me.getGameRole().getType().getName());
                                if (me.getGameRole().getType() == GameRole.Type.Wolf)
                                    bt_wolf_destroy.setVisibility(View.VISIBLE);
                            }
                        });
                    }
                })
                .on("company", new Emitter.Listener() {
                    @Override
                    public void call(Object... args) {
                        try {
                            JSONArray array = (JSONArray) args[0];
                            final List<String> companyNames = new ArrayList<String>();
                            for (int i = 0; i < array.length(); i++) {
                                String userId = (String) array.get(i);
                                MainApplication.roomInfo.findUserInRoom(userId).getGameRole().setType(1);
                                companyNames.add(MainApplication.roomInfo.findUserInRoom(userId).getNickname());
                            }

                            Log.i("你的同伴是", companyNames.toString());
                            identification_label.post(new Runnable() {
                                @Override
                                public void run() {
                                    Toast.makeText(GameMainActivity.this, "你的同伴是" + companyNames.toString(), Toast.LENGTH_SHORT).show();
                                }
                            });

                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                })
                .on("dark", new Emitter.Listener() {
                    @Override
                    public void call(Object... args) {
                        game_bg.post(new Runnable() {
                            @Override
                            public void run() {
                                tv_game_hint.setText("天黑!  请闭眼....");
                                game_bg.setBackgroundResource(R.color.dark);
                                SoundEffectManager.play(R.raw.dark);//天黑音效
                            }
                        });
                    }
                })
                .on("roomInfo", new Emitter.Listener() {
                    @Override
                    public void call(Object... args) {
                        boolean hasSaved = (boolean) args[0];
                        boolean hasPoisoned = (boolean) args[1];
                        String lastGuardedUserId = (String) args[2];
                        MainApplication.roomInfo.setHasSaved(hasSaved);
                        MainApplication.roomInfo.setHasPoisoned(hasPoisoned);
                        MainApplication.roomInfo.setLastGuardedUserId(lastGuardedUserId);
                    }
                })
                .on("light", new Emitter.Listener() {
                    @Override
                    public void call(Object... args) {
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                clearTopActivities();
                                tv_game_hint.setText("天亮了");
                                SoundEffectManager.play(R.raw.light);//天亮音效
                                game_bg.setBackgroundResource(R.color.light);
                            }
                        });
                    }
                })
                .on("votePolice", new Emitter.Listener() {
                    @Override
                    public void call(Object... args) {
                        //开始选警长。
                        identification_label.post(new Runnable() {
                            @Override
                            public void run() {
                                SoundEffectManager.stop();//声音停止
                            }
                        });
                        startActivity(new Intent(GameMainActivity.this, VoteActivity.class).putExtra("type", RoomInfo.VOTE_POLICE));
                    }
                })
                .on("voteWolf", new Emitter.Listener() {
                    @Override
                    public void call(Object... args) {
                        //开始票坏人。
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                startActivity(new Intent(GameMainActivity.this, VoteActivity.class).putExtra("type", RoomInfo.VOTE_WOLF));
                            }
                        });
                    }
                })
                .on("action", new Emitter.Listener() {
                    @Override
                    public void call(Object... args) {
                        switch (MainApplication.roomInfo.findMeInRoom().getGameRole().getType()) {
                            case Wolf:
                                startActivity(new Intent(getApplicationContext(), WolfActivity.class));
                                break;
                            case Guard:
                                startActivity(new Intent(getApplicationContext(), GuardActivity.class));
                                break;
                            case Wizard:
                                startActivity(new Intent(getApplicationContext(), WizardActivity.class));
                                break;
                            case Predictor:
                                startActivity(new Intent(getApplicationContext(), PredictorActivity.class));
                                break;
                        }
                    }
                })
                .on("darkResult", new Emitter.Listener() {
                    @Override
                    public void call(Object... args) {
                        clearTopActivities();
                        String userId1 = (String) args[0];
                        String userId2 = (String) args[1];
                        final StringBuilder sb = new StringBuilder();
                        if (userId1 == null && userId2 == null) {
                            tv_game_hint.post(new Runnable() {
                                @Override
                                public void run() {
                                    SoundEffectManager.stop();
                                    tv_game_hint.setText("今晚是平安夜");
                                }
                            });
                        } else {
                            if (userId1 != null) {
                                UserInfo info = MainApplication.roomInfo.findUserInRoom(userId1);
                                info.getGameRole().setDead(true);
                                sb.append(info.getUsername() + "被杀  ");
                            }

                            if (userId2 != null) {
                                UserInfo info = MainApplication.roomInfo.findUserInRoom(userId2);
                                info.getGameRole().setDead(true);
                                sb.append(info.getUsername() + "被杀  ");
                            }
                            tv_game_hint.post(new Runnable() {
                                @Override
                                public void run() {
                                    tv_game_hint.setText(sb.toString());
                                }
                            });
                            SoundEffectManager.play(R.raw.kill);//被杀音效
                        }
                    }
                })
                .on("hunterDead", new Emitter.Listener() {
                    @Override
                    public void call(Object... args) {
                        final String hunterId = (String) args[0];
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                tv_game_hint.setText(MainApplication.roomInfo.findUserInRoom(hunterId).getNickname()+"是猎人，等待猎人发动技能...");
                            }
                        });
                    }
                })
                .on("youHunterDead", new Emitter.Listener() {
                    @Override
                    public void call(Object... args) {
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                tv_game_hint.setText("猎人，你死了，你可以带走一个人");
                                startActivity(new Intent(GameMainActivity.this,HunterActivity.class));
                            }
                        });
                    }
                })
                .on("hunterFinished", new Emitter.Listener() {
                    @Override
                    public void call(Object... args) {
                        final String huntedId = (String) args[0];
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                if (huntedId==null) {
                                    tv_game_hint.setText("猎人没有枪杀任何人");
                                    return;
                                }
                                tv_game_hint.setText(MainApplication.roomInfo.findUserInRoom(huntedId).getNickname()+"被枪杀");
                                MainApplication.roomInfo.findUserInRoom(huntedId).getGameRole().setDead(true);
                            }
                        });
                    }
                })
                .on("changePolice", new Emitter.Listener() {
                    @Override
                    public void call(Object... args) {
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                tv_game_hint.setText("警长被杀，请等待警长移交警徽");
                            }
                        });
                    }
                })
                .on("deliverPolice", new Emitter.Listener() {
                    @Override
                    public void call(Object... args) {
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                tv_game_hint.setText("警长，你被杀了，请移交警徽");
                                startActivity(new Intent(GameMainActivity.this,PoliceActivity.class));
                            }
                        });
                    }
                })
                .on("deliverPoliceFinished", new Emitter.Listener() {
                    @Override
                    public void call(Object... args) {
                        final String userId = (String) args[0];
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                if(userId==null){
                                    tv_game_hint.setText("警徽没了");
                                    MainApplication.roomInfo.setPoliceId(null);
                                    return;
                                }
                                final UserInfo police = MainApplication.roomInfo.findUserInRoom(userId);
                                MainApplication.roomInfo.setPoliceId(userId);
                                tv_game_hint.setText("警徽传给了"+ police.getNickname());
                            }
                        });
                    }
                })
                .on("leaveWords", new Emitter.Listener() {
                    @Override
                    public void call(final Object... args) {
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                String userId = (String) args[0];
                                tv_game_hint.setText("下面请"+MainApplication.roomInfo.findUserInRoom(userId).getNickname()+"发表遗言");
                            }
                        });
                    }
                })
                .on("youLeaveWords", new Emitter.Listener() {
                    @Override
                    public void call(Object... args) {
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                isLeavingWords = true;
                                voiceManager.startRecord();
                                speak_time_label.setVisibility(View.VISIBLE);
                                bt_endSpeak.setEnabled(true);
                                bigHead.setImageURI(MainApplication.roomInfo.findMeInRoom().getHead());
                                gallery.setSelection(MainApplication.roomInfo.findMyIndexInRoom());
                                tickTimer =new TickTimer(time_label,40,null){
                                    @Override
                                    protected void onTimeEnd() {
                                        super.onTimeEnd();
                                        MainApplication.socket.emit("leaveWordsFinished", MainApplication.roomInfo.getRoomId(),true);
                                    }
                                };
                                tickTimer.startTick();
                                tv_game_hint.setText("你被刀了,请发表遗言");
                                Toast.makeText(GameMainActivity.this, "你被刀了,请发表遗言", Toast.LENGTH_SHORT).show();
                            }
                        });
                    }
                })
                .on("startSpeak", new Emitter.Listener() {
                    @Override
                    public void call(Object... args) {
                        identification_label.post(new Runnable() {
                            @Override
                            public void run() {
                                startActivity(new Intent(GameMainActivity.this, GameMainActivity.class));
                                tv_game_hint.setText("现在发言开始");
                                speakAnim.start();
                            }
                        });

                    }
                })
                .on("speak", new Emitter.Listener() {
                    @Override
                    public void call(Object... args) {
                        final String userId = (String) args[0];
                        currentUser = MainApplication.roomInfo.findUserInRoom(userId);
                        identification_label.post(new Runnable() {
                            @Override
                            public void run() {
                                voiceManager.startPlay();
                                bigHead.setImageURI(MainApplication.roomInfo.findUserInRoom(userId).getHead());
                                gallery.setSelection(MainApplication.roomInfo.findUserIndexInRoom(userId));
                                tv_game_hint.setText("现在" + MainApplication.roomInfo.findUserInRoom(userId).getUsername() + "开始发言");
                            }
                        });

                    }
                })
                .on("youSpeak", new Emitter.Listener() {
                    @Override
                    public void call(Object... args) {
                        currentUser = MainApplication.roomInfo.findMeInRoom();
                        identification_label.post(new Runnable() {
                            @Override
                            public void run() {
                                voiceManager.startRecord();
                                speak_time_label.setVisibility(View.VISIBLE);
                                tv_game_hint.setVisibility(View.INVISIBLE);
                                bt_endSpeak.setEnabled(true);
                                bigHead.setImageURI(MainApplication.roomInfo.findMeInRoom().getHead());
                                gallery.setSelection(MainApplication.roomInfo.findMyIndexInRoom());
                                tickTimer =new TickTimer(time_label,40,null){
                                    @Override
                                    protected void onTimeEnd() {
                                        super.onTimeEnd();
                                        finishSpeak();
                                        MainApplication.socket.emit("pass", MainApplication.roomInfo.getRoomId());
                                    }
                                };
                                tickTimer.startTick();
                                if(MainApplication.roomInfo.findMeInRoom().getGameRole().getType()== GameRole.Type.Wolf)
                                    bt_wolf_destroy.setEnabled(true);
                                tv_game_hint.setText("现在轮到你发言");
                                Toast.makeText(GameMainActivity.this, "现在轮到你发言", Toast.LENGTH_SHORT).show();
                            }
                        });

                    }
                })
                .on("endSpeak", new Emitter.Listener() {
                    @Override
                    public void call(Object... args) {
                        identification_label.post(new Runnable() {
                            @Override
                            public void run() {
                                tv_game_hint.setText("发言结束");
                                speakAnim.stop();
                            }
                        });
                    }
                })
                .on("blob", new Emitter.Listener() {
                    @Override
                    public void call(Object... args) {
                        byte[] buffer = (byte[]) args[0];
                        voiceManager.speak(buffer);
                    }
                })
                .on("gameOver", new Emitter.Listener() {
                    @Override
                    public void call(final Object... args) {
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                try {
                                    JSONObject gameResult = (JSONObject) args[0];
                                    int victory = gameResult.getInt("victory");
                                    Intent intent=new Intent(GameMainActivity.this,GameOverActivity.class);
                                    if(victory==0){
                                        intent.putExtra("victory","狼人胜利");
                                    }else if(victory==1){
                                        intent.putExtra("victory","好人胜利");
                                    }
                                    JSONArray array = gameResult.getJSONArray("returnResults");
                                    for(int i=0;i<array.length();i++){
                                        JSONObject obj = (JSONObject) array.get(i);
                                        String userId = obj.getString("userId");
                                        int type =obj.getInt("type");
                                        int score = obj.getInt("score");
                                        GameRole gameRole =MainApplication.roomInfo.findUserInRoom(userId).getGameRole();
                                        gameRole.setType(type);
                                        gameRole.setScore(score);

                                    }
                                    startActivity(intent);
                                    finish();

                                }catch (Exception e){
                                    e.printStackTrace();
                                }
                            }
                        });

                    }
                })
                .on("wolfDestroy", new Emitter.Listener() {
                    @Override
                    public void call(Object... args) {
                        final String wolfId = (String) args[0];
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                UserInfo wolf = MainApplication.roomInfo.findUserInRoom(wolfId);
                                wolf.getGameRole().setType(1);
                                tv_game_hint.setText("狼人 "+ wolf.getNickname()+" 自爆了");
                                Toast.makeText(GameMainActivity.this, "狼人 "+ wolf.getNickname()+" 自爆了", Toast.LENGTH_SHORT).show();
                                finishSpeak();
                            }
                        });
                    }
                });

    }


    @Override
    protected void unbindSocket() {

        voiceManager.stopPlay();
        voiceManager.stopRecord();
        MainApplication.socket.off("start").off("company").off("blob");
    }


    //弹出游戏规则；标记身份
    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.iv_gameStage_gameRule:
                SoundEffectManager.play(R.raw.user_detail);
                popupWindow.showAtLocation(v, Gravity.CENTER,0,0);
                break;
            case R.id.tv_playStage_identification:
                SoundEffectManager.play(R.raw.user_detail);
                Identification();
                break;
        }
    }


    @Override
    public void onBackPressed() {

    }

    //标记玩家身份
    public void Identification() {
        final AlertDialog.Builder builder = new AlertDialog.Builder(GameMainActivity.this);
        builder.setTitle("请标记玩家身份");
        builder.setSingleChoiceItems(getResources().getStringArray(R.array.sign_type), 0, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                currentUser.getGameRole().setSign_type(which);
                dialog.dismiss();
            }
        });

        builder.setNegativeButton("取消", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                Toast.makeText(GameMainActivity.this, "未标记", Toast.LENGTH_SHORT).show();
                dialog.dismiss();//消失对话框
            }
        });
        builder.show();
    }


}

