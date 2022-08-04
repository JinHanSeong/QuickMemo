package com.puresoftware.quickmemo.floating;

import static com.puresoftware.quickmemo.R.drawable.ic_bold_selected;
import static com.puresoftware.quickmemo.R.drawable.ic_bold_solid;
import static com.puresoftware.quickmemo.R.drawable.ic_strikethrough_selected;
import static com.puresoftware.quickmemo.R.drawable.ic_strikethrough_solid;
import static com.puresoftware.quickmemo.R.drawable.ic_underline_selected;
import static com.puresoftware.quickmemo.R.drawable.ic_underline_solid;
import static com.puresoftware.quickmemo.R.drawable.ic_write_activity_lock_solid;
import static com.puresoftware.quickmemo.R.drawable.ic_write_activity_star_regular;
import static com.puresoftware.quickmemo.R.drawable.ic_write_activity_star_selected;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.drawable.BitmapDrawable;
import android.os.Build;
import android.os.IBinder;
import android.text.TextUtils;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.PopupWindow;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.core.app.NotificationCompat;

import com.puresoftware.quickmemo.MainActivity;
import com.puresoftware.quickmemo.R;
import com.puresoftware.quickmemo.WriteActivity;
import com.puresoftware.quickmemo.room.AppDatabase;
import com.puresoftware.quickmemo.room.Memo;
import com.puresoftware.quickmemo.room.MemoDao;

import java.util.Calendar;

import jp.wasabeef.richeditor.RichEditor;

public class WidgetService extends Service {

    String TAG = WidgetService.class.getSimpleName();

    int LAYOUT_FLAG;
    View mFloatingView;
    WindowManager windowManager;
    ImageView imageClose;
    ImageView imageCloseSelect;
    TextView tvWidth;
    float height, width;
    long clickDuration;

    //title line
    ImageView btnBack;
    EditText edtTitle;
    ImageView btnStar;
    ImageView btnLock;

    //switch
    boolean starSwitch = false;
    boolean lockSwitch = false;
    boolean boldSwitch = false;
    boolean cancelLineSwitch = false;
    boolean underLineSwitch = false;

    //textLine
    RichEditor richEditor;

    //menuLine
    TextView btnFontSize;
    ImageView btnColor;
    ImageView btnAlignment;
    ImageView btnBold;
    ImageView btnCancelLine;
    ImageView btnUnderLine;
    boolean isInit = false;



    private ViewGroup floatView;
    private WindowManager.LayoutParams floatViewLayoutParams;

    // 플로팅 위젯 뷰가 왼쪽에 있는지 오른쪽에 있는지 확인하는 변수
    // 처음에는 플로팅 위젯 뷰를 오른쪽에 표시하므로 false로 설정
    private boolean isLeft = false;

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {

        // startForground에러나 bad Notification 관련 에러로 인한 Notification 만듬(숙지 안됨)
        // https://ddolcat.tistory.com/259
        String CHANNEL_ID = "channel_1";
        NotificationChannel channel = new NotificationChannel(CHANNEL_ID,"Andorid test", NotificationManager.IMPORTANCE_LOW);
        ((NotificationManager)getSystemService(Context.NOTIFICATION_SERVICE)).createNotificationChannel(channel);

        Notification notification = new NotificationCompat.Builder(this,CHANNEL_ID)
                .setContentTitle("").setContentText("").build();
        startForeground(2,notification);
        // https://ddolcat.tistory.com/259


        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            LAYOUT_FLAG = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;

        } else {
            LAYOUT_FLAG = WindowManager.LayoutParams.TYPE_PHONE;
        }

        // 우리가 만든 플로팅 뷰 레이아웃 확장
        mFloatingView = LayoutInflater.from(this).inflate(R.layout.layout_widget, null);

        WindowManager.LayoutParams layoutParams = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,

                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT);

        // 보기 위치 지정
        // 처음에는보기가 오른쪽 상단 모서리에 추가되며 필요에 따라 x-y 좌표를 변경
        layoutParams.gravity = Gravity.TOP | Gravity.RIGHT;
        layoutParams.x = 0;
        layoutParams.y = 100;

        WindowManager.LayoutParams imageParams = new WindowManager.LayoutParams(140,
                140,
                LAYOUT_FLAG,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT);
        imageParams.gravity = Gravity.BOTTOM | Gravity.CENTER;
        imageParams.y = 100;


        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        imageClose = new ImageView(this);
        imageClose.setImageResource(R.drawable.ic_baseline_delete_outline_24);
        imageClose.setVisibility(View.INVISIBLE);

        imageCloseSelect = new ImageView(this);
        imageCloseSelect.setImageResource(R.drawable.ic_baseline_delete_outline_select24);

        windowManager.addView(imageClose, imageParams);
        windowManager.addView(mFloatingView, layoutParams);
        mFloatingView.setVisibility(View.VISIBLE);

        height = windowManager.getDefaultDisplay().getHeight();
        width = windowManager.getDefaultDisplay().getWidth();

        tvWidth = (TextView) mFloatingView.findViewById(R.id.imageView);

        // 사용자의 터치 동작을 사용하여 플로팅 뷰를 드래그하여 이동
        tvWidth.setOnTouchListener(new View.OnTouchListener() {

            int initialx, initialy;
            float initialTouchX, initialTouchY;
            long startCkickTime;

            // 클릭으로 볼 최대시간
            int MAX_CLICK_DURATION = 200;

            @Override
            public boolean onTouch(View view, MotionEvent motionEvent) {

                switch (motionEvent.getAction()) {

                    case MotionEvent.ACTION_DOWN:

                        startCkickTime = Calendar.getInstance().getTimeInMillis(); // 처음 클릭한 시간
                        imageClose.setVisibility(View.VISIBLE);

                        // 초기 위치 기억
                        initialx = layoutParams.x;
                        initialy = layoutParams.y;

                        //터치 위치 좌표 얻기
                        initialTouchX = motionEvent.getRawX();
                        initialTouchY = motionEvent.getRawY();

                        return true;

                    case MotionEvent.ACTION_UP:

                        clickDuration = Calendar.getInstance().getTimeInMillis() - startCkickTime; // 지금 손 뗀 시간
                        imageClose.setVisibility(view.GONE);
                        Log.i(TAG, "click time:" + clickDuration);

                        // 초기 좌표와 현재 좌표의 차이 가져 오기
                        layoutParams.x = initialx + (int) (initialTouchX - motionEvent.getRawX());
                        layoutParams.y = initialy + (int) (motionEvent.getRawY() - initialTouchY);

                        // 사용자가 플로팅 위젯을 제거 이미지로 끌어다 놓으면 서비스를 중지합니다.
                        if (clickDuration >= MAX_CLICK_DURATION) {
                            // 제거 이미지 주변 거리
                            if (layoutParams.y > (height * 0.8)) {
                                stopSelf();
                            }

                            //사용자가 플로팅 뷰를 드래그하면 위치 재설정
                            if (layoutParams.x <= 500) {
                                isLeft = false;
                                layoutParams.x = 0;
                                windowManager.updateViewLayout(mFloatingView, layoutParams);

                            } else {
                                isLeft = true;
                                layoutParams.x = (int) width; // R값에 고정값 대신 View의 Right를 가져옴. 해당코드는 따로 예제를 다시 만들어야 한다.
                                windowManager.updateViewLayout(mFloatingView, layoutParams);
                            }
                        }

                        // 플로팅으로 writeActivity띄우기
                        if(!isInit) {
                            isInit = true;
                            LayoutInflater inflater = (LayoutInflater) getBaseContext().getSystemService(LAYOUT_INFLATER_SERVICE);

                            floatView = (ViewGroup) inflater.inflate(R.layout.activity_write, null); // 서비스 실행 시 View 를 새로 Inflate 한다.
//                        Toast.makeText(WidgetService.this, "이것은 액션 업입니다.", Toast.LENGTH_SHORT).show();
                            floatViewLayoutParams = new WindowManager.LayoutParams(
                                    ((int) (width * 0.9f)), // 축소 될때 윈도우 해상도 값. X축
                                    ((int) (height * 0.8f)), // 축소 될때 윈도우 해상도 값. Y축
                                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                                    0,
                                    PixelFormat.TRANSLUCENT
                            );

                            btnBack = floatView.findViewById(R.id.btn_write_activity_back);
                            btnBack.setImageDrawable(getDrawable(R.drawable.ic_baseline_arrow_back_ios_24));
                            edtTitle = floatView.findViewById(R.id.edt_write_activity_title);
                            btnStar = floatView.findViewById(R.id.btn_write_activity_star);
                            btnStar.setImageDrawable(getDrawable(R.drawable.ic_write_activity_star_regular));
                            btnLock = floatView.findViewById(R.id.btn_write_activity_lock);
                            btnLock.setImageDrawable(getDrawable(R.drawable.ic_write_activity_lock_solid));
                            richEditor = floatView.findViewById(R.id.v_write_activity_richeditor);

                            btnFontSize = (TextView) floatView.findViewById(R.id.btn_write_activity_fontsize);
                            btnColor = floatView.findViewById(R.id.btn_write_activity_color);
                            btnColor.setImageDrawable(getDrawable(R.drawable.ic_palette_solid));
                            btnAlignment = floatView.findViewById(R.id.btn_write_activity_alignment);
                            btnAlignment.setImageDrawable(getDrawable(R.drawable.ic_align_justify_solid));
                            btnBold = floatView.findViewById(R.id.btn__write_activity_bold);
                            btnBold.setImageDrawable(getDrawable(R.drawable.ic_bold_solid));
                            btnCancelLine = floatView.findViewById(R.id.btn__write_activity_canceline);
                            btnCancelLine.setImageDrawable(getDrawable(R.drawable.ic_strikethrough_solid));
                            btnUnderLine = floatView.findViewById(R.id.btn__write_activity_underline);
                            btnUnderLine.setImageDrawable(getDrawable(R.drawable.ic_underline_solid));

                            // 기본으로 사용할 폰트의 크기
                            richEditor.setFontSize(5);

                            floatViewLayoutParams.gravity = Gravity.CENTER;
                            floatViewLayoutParams.x = 0;
                            floatViewLayoutParams.y = 0;
                            floatView.setFocusable(true);
                            windowManager.addView(floatView, floatViewLayoutParams);


                            floatView.setOnTouchListener(new View.OnTouchListener() {
                                final WindowManager.LayoutParams updateWindowParam = floatViewLayoutParams;
                                double x = 0.0;
                                double y = 0.0;
                                double px = 0.0;
                                double py = 0.0;


                                @Override
                                public boolean onTouch(View v, MotionEvent event) {
                                    switch (event.getAction()) {
                                        case MotionEvent.ACTION_DOWN:
                                            x = (double) updateWindowParam.x;
                                            y = (double) updateWindowParam.y;

                                            px = (double) event.getRawX();
                                            py = (double) event.getRawY();
                                            break;
                                        case MotionEvent.ACTION_MOVE:
                                            updateWindowParam.x = (int) (x + event.getRawX() - px);
                                            updateWindowParam.y = (int) (y + event.getRawY() - py);
                                            windowManager.updateViewLayout(floatView, updateWindowParam);
                                            break;
                                    }

                                    return false;
                                }
                            });


                            btnBack.setOnClickListener(v -> {
                                // RoomDB 객체
                                AppDatabase db = AppDatabase.getInstance(getBaseContext());
                                MemoDao memoDao = db.dao();

                                new Thread(new Runnable() {
                                    @Override
                                    public void run() {

                                    }
                                });

                                String title = edtTitle.getText().toString().trim();
                                String content = richEditor.getHtml();
                                boolean star = starSwitch;
                                boolean lock = lockSwitch;
                                long timeStamp = System.currentTimeMillis();

                                // 외부라이브러리에서 TextNull은 TextUtils.isEmpty로 해야 함.
                                if (title.trim().equals("") && TextUtils.isEmpty(content)) {
                                    Log.i(TAG, "memo null");
                                } else {
                                    new Thread(new Runnable() {
                                        @Override
                                        public void run() {
                                            Memo memo = new Memo();

                                            memo.title = title;
                                            memo.content = content;
                                            memo.lock = lock;
                                            memo.star = star;
                                            memo.timestamp = timeStamp;
                                            memoDao.insert(memo);


                                            Log.i(TAG, "memoData:" + memo.toString());
                                            Log.i(TAG, "memo completed");

                                        }
                                    }).start();
                                }
                                windowManager.removeView(floatView);
                                isInit = false;
                            });

                            btnStar.setOnClickListener(v -> {

                                starSwitch = !starSwitch;
                                Log.i(TAG, "starSwitch status: " + starSwitch + "");

                                if (starSwitch == false) {
                                    btnStar.setImageResource(ic_write_activity_star_regular);
                                } else {
                                    btnStar.setImageResource(ic_write_activity_star_selected);
                                }
                            });

                            btnLock.setOnClickListener(new View.OnClickListener() {
                                @Override
                                public void onClick(View view) {

                                    lockSwitch = !lockSwitch;
                                    Log.i(TAG, "starSwitch status: " + lockSwitch + "");

                                    if (lockSwitch == false) {
                                        btnLock.setImageResource(ic_write_activity_lock_solid);
                                    } else {
                                        btnLock.setImageResource(R.drawable.ic_write_activity_lock_selected);
                                    }
                                }
                            });


                            btnFontSize.setOnClickListener(view1 -> {

                                //팝업윈도우 구현
                                PopupWindow popupWindow = new PopupWindow(view1);
                                LayoutInflater inflater1 = (LayoutInflater) getSystemService(Context.LAYOUT_INFLATER_SERVICE);
                                View inflateView = inflater1.inflate(R.layout.write_layout_fontsize, null);
                                popupWindow.setContentView(inflateView);
                                popupWindow.setWindowLayoutMode(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);

                                //팝업윈도우 파라미터
                                popupWindow.setAnimationStyle(R.style.Animation_AppCompat_Dialog); // 안드로이드 기본 애니메이션임. 이걸 넣어야 상향일때 애니메이션이 적용됨.
                                popupWindow.setTouchable(true);
                                popupWindow.setFocusable(true); //팝업 뷰 포커스도 주고
                                popupWindow.update();
                                popupWindow.setOutsideTouchable(true); //팝업 뷰 이외에도 터치되게 (터치시 팝업 닫기 위한 코드)
                                popupWindow.setBackgroundDrawable(new BitmapDrawable());

//                popupWindow.showAsDropDown(btnFontSize, 0, -50);
//                popupWindow.showAtLocation(btnFontSize, Gravity.LEFT, 0,
//                        btnFontSize.getBottom() - 60);
                                popupWindow.showAsDropDown(btnFontSize, 0, -310);

//                popupWindow.showAsDropDown(btnFontSize, 0, btnFontSize.getBottom() - 60);

                                TextView tvFontSmall = inflateView.findViewById(R.id.tv_write_activity_menu_align_ledt);
                                TextView tvFontMid = inflateView.findViewById(R.id.tv_write_activity_menu_align_center);
                                TextView tvFontBig = inflateView.findViewById(R.id.tv_write_activity_menu_align_right);

                                tvFontSmall.setOnClickListener(new View.OnClickListener() {
                                    @Override
                                    public void onClick(View view1) {
                                        richEditor.setFontSize(3);
                                        popupWindow.dismiss();
                                    }
                                });

                                tvFontMid.setOnClickListener(new View.OnClickListener() {
                                    @Override
                                    public void onClick(View view1) {
                                        richEditor.setFontSize(5);
                                        popupWindow.dismiss();
                                    }
                                });

                                tvFontBig.setOnClickListener(new View.OnClickListener() {
                                    @Override
                                    public void onClick(View view1) {
                                        richEditor.setFontSize(10);
                                        popupWindow.dismiss();
                                    }
                                });
                            });

                            btnColor.setOnClickListener(new View.OnClickListener() {
                                @Override
                                public void onClick(View view) {

                                    //팝업윈도우 구현
                                    PopupWindow popupWindow = new PopupWindow(view);
                                    LayoutInflater inflater = (LayoutInflater) getSystemService(Context.LAYOUT_INFLATER_SERVICE);
                                    View inflateView = inflater.inflate(R.layout.write_layout_color, null);

                                    popupWindow.setContentView(inflateView);
                                    popupWindow.setWindowLayoutMode(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);

                                    //팝업윈도우 파라미터
                                    popupWindow.setAnimationStyle(R.style.Animation_AppCompat_Dialog); // 안드로이드 기본 애니메이션임. 이걸 넣어야 상향일때 애니메이션이 적용됨.
                                    popupWindow.setTouchable(true);
                                    popupWindow.setFocusable(true); //팝업 뷰 포커스도 주고
                                    popupWindow.update();
                                    popupWindow.setOutsideTouchable(true); //팝업 뷰 이외에도 터치되게 (터치시 팝업 닫기 위한 코드)
                                    popupWindow.setBackgroundDrawable(new BitmapDrawable());

                                    popupWindow.showAsDropDown(btnColor, 0, -310);

                                    ImageView btnMenuRed = inflateView.findViewById(R.id.tv_write_activity_menu_color_red);
                                    btnMenuRed.setImageDrawable(getDrawable(R.drawable.write_activity_color_red));
                                    ImageView btnMenuOrange = inflateView.findViewById(R.id.tv_write_activity_menu_color_orange);
                                    btnMenuOrange.setImageDrawable(getDrawable(R.drawable.write_activity_color_orange));
                                    ImageView btnMenuYellow = inflateView.findViewById(R.id.tv_write_activity_menu_color_yellow);
                                    btnMenuYellow.setImageDrawable(getDrawable(R.drawable.write_activity_color_yellow));
                                    ImageView btnMenuGreen = inflateView.findViewById(R.id.tv_write_activity_menu_color_green);
                                    btnMenuGreen.setImageDrawable(getDrawable(R.drawable.write_activity_color_green));
                                    ImageView btnMenuSky = inflateView.findViewById(R.id.tv_write_activity_menu_color_sky);
                                    btnMenuSky.setImageDrawable(getDrawable(R.drawable.write_activity_color_sky));
                                    ImageView btnMenuBlue = inflateView.findViewById(R.id.tv_write_activity_menu_color_blue);
                                    btnMenuBlue.setImageDrawable(getDrawable(R.drawable.write_activity_color_blue));
                                    ImageView btnMenuPurple = inflateView.findViewById(R.id.tv_write_activity_menu_color_purple);
                                    btnMenuPurple.setImageDrawable(getDrawable(R.drawable.write_activity_color_purple));
                                    ImageView btnMenuBlack = inflateView.findViewById(R.id.tv_write_activity_menu_color_black);
                                    btnMenuBlack.setImageDrawable(getDrawable(R.drawable.write_activity_color_black));

                                    btnMenuRed.setOnClickListener(new View.OnClickListener() {
                                        @Override
                                        public void onClick(View view) {
                                            richEditor.setTextColor(Color.parseColor("#FF4D4D"));
                                            popupWindow.dismiss();

                                        }
                                    });

                                    btnMenuOrange.setOnClickListener(new View.OnClickListener() {
                                        @Override
                                        public void onClick(View view) {
                                            richEditor.setTextColor(Color.parseColor("#FFA94D"));
                                            popupWindow.dismiss();

                                        }
                                    });

                                    btnMenuYellow.setOnClickListener(new View.OnClickListener() {
                                        @Override
                                        public void onClick(View view) {
                                            richEditor.setTextColor(Color.parseColor("#FFED4D"));
                                            popupWindow.dismiss();

                                        }
                                    });

                                    btnMenuGreen.setOnClickListener(new View.OnClickListener() {
                                        @Override
                                        public void onClick(View view) {
                                            richEditor.setTextColor(Color.parseColor("#8BFF4D"));
                                            popupWindow.dismiss();

                                        }
                                    });

                                    btnMenuSky.setOnClickListener(new View.OnClickListener() {
                                        @Override
                                        public void onClick(View view) {
                                            richEditor.setTextColor(Color.parseColor("#4DC1FF"));
                                            popupWindow.dismiss();

                                        }
                                    });

                                    btnMenuBlue.setOnClickListener(new View.OnClickListener() {
                                        @Override
                                        public void onClick(View view) {
                                            richEditor.setTextColor(Color.parseColor("#4D65FF"));
                                            popupWindow.dismiss();

                                        }
                                    });

                                    btnMenuPurple.setOnClickListener(new View.OnClickListener() {
                                        @Override
                                        public void onClick(View view) {
                                            richEditor.setTextColor(Color.parseColor("#A04DFF"));
                                            popupWindow.dismiss();

                                        }
                                    });

                                    btnMenuBlack.setOnClickListener(new View.OnClickListener() {
                                        @Override
                                        public void onClick(View view) {
                                            richEditor.setTextColor(Color.parseColor("#000000"));
                                            popupWindow.dismiss();

                                        }
                                    });

                                }
                            });

                            btnAlignment.setOnClickListener(new View.OnClickListener() {
                                @Override
                                public void onClick(View view) {

                                    //팝업윈도우 구현
                                    PopupWindow popupWindow = new PopupWindow(view);
                                    LayoutInflater inflater = (LayoutInflater) getSystemService(Context.LAYOUT_INFLATER_SERVICE);
                                    View inflateView = inflater.inflate(R.layout.write_layout_alignment, null);
                                    popupWindow.setContentView(inflateView);
                                    popupWindow.setWindowLayoutMode(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);

                                    //팝업윈도우 파라미터
                                    popupWindow.setAnimationStyle(R.style.Animation_AppCompat_Dialog); // 안드로이드 기본 애니메이션임. 이걸 넣어야 상향일때 애니메이션이 적용됨.
                                    popupWindow.setTouchable(true);
                                    popupWindow.setFocusable(true); //팝업 뷰 포커스도 주고
                                    popupWindow.update();
                                    popupWindow.setOutsideTouchable(true); //팝업 뷰 이외에도 터치되게 (터치시 팝업 닫기 위한 코드)
                                    popupWindow.setBackgroundDrawable(new BitmapDrawable());

                                    popupWindow.showAsDropDown(btnAlignment, 0, -310);

                                    TextView tvLeft = inflateView.findViewById(R.id.tv_write_activity_menu_align_ledt);
                                    TextView tvCenter = inflateView.findViewById(R.id.tv_write_activity_menu_align_center);
                                    TextView tvRight = inflateView.findViewById(R.id.tv_write_activity_menu_align_right);

                                    tvLeft.setOnClickListener(new View.OnClickListener() {
                                        @Override
                                        public void onClick(View view) {
                                            richEditor.setAlignLeft();
                                        }
                                    });

                                    tvCenter.setOnClickListener(new View.OnClickListener() {
                                        @Override
                                        public void onClick(View view) {
                                            richEditor.setAlignCenter();
                                        }
                                    });

                                    tvRight.setOnClickListener(new View.OnClickListener() {
                                        @Override
                                        public void onClick(View view) {
                                            richEditor.setAlignRight();
                                        }
                                    });
                                }
                            });

                            btnBold.setOnClickListener(new View.OnClickListener() {
                                @Override
                                public void onClick(View view) {

                                    if (boldSwitch == false) {
                                        boldSwitch = true;
                                        btnBold.setImageResource(ic_bold_selected);
                                        richEditor.setBold();
                                    } else {
                                        boldSwitch = false;
                                        btnBold.setImageResource(ic_bold_solid);
                                        richEditor.setBold();
                                    }
                                }
                            });

                            btnCancelLine.setOnClickListener(new View.OnClickListener() {
                                @Override
                                public void onClick(View view) {
                                    if (cancelLineSwitch == false) {
                                        cancelLineSwitch = true;
                                        btnCancelLine.setImageResource(ic_strikethrough_selected);
                                        richEditor.setStrikeThrough();
                                    } else {
                                        cancelLineSwitch = false;
                                        btnCancelLine.setImageResource(ic_strikethrough_solid);
                                        richEditor.setStrikeThrough();

                                    }
                                }
                            });

                            btnUnderLine.setOnClickListener(new View.OnClickListener() {
                                @Override
                                public void onClick(View view) {

                                    if (underLineSwitch == false) {
                                        underLineSwitch = true;
                                        btnUnderLine.setImageResource(ic_underline_selected);
                                        richEditor.setUnderline();
                                    } else {
                                        underLineSwitch = false;
                                        btnUnderLine.setImageResource(ic_underline_solid);
                                        richEditor.setUnderline();
                                    }
                                }
                            });
                        }
                        return true;

                    case MotionEvent.ACTION_MOVE:

                        // 초기 좌표와 현재 좌표의 차이 가져 오기
                        layoutParams.x = initialx + (int) (initialTouchX - motionEvent.getRawX());
                        layoutParams.y = initialy + (int) (motionEvent.getRawY() - initialTouchY);

                        // 새로운 X 및 Y 좌표로 레이아웃 업데이트
                        windowManager.updateViewLayout(mFloatingView, layoutParams);

                        if (layoutParams.y > (height * 0.8)) {

                            imageClose.setImageResource(R.drawable.ic_baseline_delete_outline_select24);
                        } else {
                            imageClose.setImageResource(R.drawable.ic_baseline_delete_outline_24);
                        }
                        return true;
                }
                return false;
            }
        });
        return START_STICKY;
    }

    // 앱이 종료될때 실행
    @Override
    public void onDestroy() {
        super.onDestroy();

        if (mFloatingView != null) {
            windowManager.removeView(mFloatingView);
        }
        if (imageClose != null) {
            windowManager.removeView(imageClose);
        }
    }
}
