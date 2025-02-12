package com.example.bmap1_map;

import android.app.Activity;
import android.content.Context;
import android.os.Bundle;
import android.view.View;
import android.view.ViewTreeObserver;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.baidu.location.BDAbstractLocationListener;
import com.baidu.location.BDLocation;
import com.baidu.location.Poi;
import com.baidu.location.PoiRegion;
import com.baidu.mapapi.map.BaiduMap;
import com.baidu.mapapi.map.CircleOptions;
import com.baidu.mapapi.map.MapStatus;
import com.baidu.mapapi.map.MapStatusUpdateFactory;
import com.baidu.mapapi.map.MapView;
import com.baidu.mapapi.map.MyLocationConfiguration;
import com.baidu.mapapi.map.MyLocationData;
import com.baidu.mapapi.map.OverlayOptions;
import com.baidu.mapapi.map.Stroke;
import com.baidu.mapapi.model.LatLng;
import com.baidu.mapapi.search.core.PoiInfo;
import com.baidu.mapapi.search.core.SearchResult;
import com.baidu.mapapi.search.poi.OnGetPoiSearchResultListener;
import com.baidu.mapapi.search.poi.PoiDetailResult;
import com.baidu.mapapi.search.poi.PoiDetailSearchResult;
import com.baidu.mapapi.search.poi.PoiIndoorResult;
import com.baidu.mapapi.search.poi.PoiNearbySearchOption;
import com.baidu.mapapi.search.poi.PoiResult;
import com.example.bmap1_map.service.LocationService;
import com.example.bmap1_map.service.PoiOverlay;
import com.example.bmap1_map.service.PoiSearchService;

import java.util.List;

public class MainActivity extends Activity {

    public MapView mMapView;
    public BaiduMap mMap;
    private LocationService locationService;
    private TextView LocationResult;
    private Button searchPOI;
    private Button searchNext;
    private int locTimes = 0;//定位次数，用于控制地图更新动作（仅第一次调整中心和比例）
    private PoiSearchService poiSearchService;
    private EditText mEditRadius;//半径输入框
    private RelativeLayout mPoiDetailView;
    private TextView mPoiResult;
    private List<PoiInfo> mAllPoi;
    private LatLng ll;
    private int radius;//半径
    private int num = 0;//检索分页数量


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_poinearbysearch);
//        setContentView(R.layout.main_activity1);//测试新的布局

        // -----------demo view config ------------
        LocationResult = (TextView) findViewById(R.id.textView);//定位结果展示栏
        mEditRadius = (EditText) findViewById(R.id.edit_radius);//半径输入栏
        searchPOI = (Button) findViewById(R.id.search_poi);//检索按钮
        searchNext = (Button) findViewById(R.id.search_next);//下一组按钮
        mMapView = (MapView) findViewById(R.id.bmapView);//地图
        mMap = mMapView.getMap();//获取地图控件对象
        mMap.setMyLocationEnabled(true);//开启定位地图图层
        mPoiDetailView = (RelativeLayout) findViewById(R.id.poi_detail);
        mPoiResult = (TextView) findViewById(R.id.poi_result);//POI检索信息展示

        //获取locationservice实例
        locationService = ((Map) getApplication()).locationService;
        //注册监听
        locationService.registerListener(mListener);
        //设置定位参数
        locationService.setLocationOption(locationService.getDefaultLocationClientOption());

        //创建POI检索实例并注册监听
        poiSearchService = new PoiSearchService(poiListener);
    }

    @Override
    protected void onStart() {
        super.onStart();
        //开始定位
        locationService.start();// 定位SDK
        locTimes = 0;//定位次数置零
    }

    @Override
    protected void onResume() {
        super.onResume();

        //开始检索+上一组检索
        searchPOI.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {

                radius = Integer.parseInt(mEditRadius.getText().toString());//获取半径
                if (searchPOI.getText().toString().equals(getString(R.string.startsearch))) {
                    //开始检索
                    num = 0;
                    searchNearby(radius, num);
                } else if(num > 1){
                    num--;
                    searchNearby(radius, num);
                }else if(num ==1){
                    num--;
                    searchNearby(radius, num);
                    searchPOI.setText(getString(R.string.startsearch));
                }
            }
        });

        //下一组检索
        searchNext.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                //检索下一组
                radius = Integer.parseInt(mEditRadius.getText().toString());//获取半径
                num++;
                searchNearby(radius, num);
                //检索按钮变为“上一组”
                searchPOI.setText(getString(R.string.previous));
            }
        });

        //重新输入半径，重置检索
        mEditRadius.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                searchPOI.setText(getString(R.string.startsearch));
            }
        });
    }

    @Override
    protected void onStop() {
        // TODO Auto-generated method stub
        locationService.unregisterListener(mListener); //注销掉监听
        locationService.stop(); //停止定位服务
        super.onStop();
    }


    //实现定位监听
    private BDAbstractLocationListener mListener = new BDAbstractLocationListener() {

        @Override
        public void onReceiveLocation(BDLocation location) {
            // map view 销毁后不在处理新接收的位置
            if (location == null || mMapView == null) {
                return;
            }

            //仅当第一次定位，调整中心和比例
            locTimes++;
            if (locTimes == 1) {
                //构造地理坐标数据
                ll = new LatLng(location.getLatitude(),
                        location.getLongitude());
                //（以动画方式）改变地图状态（中心、倍数等）
                MapStatus.Builder builder = new MapStatus.Builder();
                builder.target(ll).zoom(17.0f);
                mMap.animateMapStatus(MapStatusUpdateFactory.newMapStatus(builder.build()));
            }

            //构造定位数据
            MyLocationData locData = new MyLocationData.Builder()
                    .accuracy(location.getRadius())
                    // 此处设置开发者获取到的方向信息，顺时针0-360
                    .direction(location.getDirection())
                    .latitude(location.getLatitude())
                    .longitude(location.getLongitude())
                    .build();
            //设置定位数据，设置并显示定位蓝点
//            BitmapDescriptor BitmapDescriptor = BitmapDescriptorFactory.fromResource(R.drawable.location_marker);//自定义图标
            MyLocationConfiguration mLocationConfig = new MyLocationConfiguration(MyLocationConfiguration.LocationMode.NORMAL, true, null);
            mMap.setMyLocationConfiguration(mLocationConfig);
            mMap.setMyLocationData(locData);//显示定位蓝点、

            //检测定位结果
            StringBuffer sb = new StringBuffer(256);
            sb.append("结果：\n");
            sb.append("lat:");
            sb.append(location.getLatitude() + "\n");
            sb.append("lon:");
            sb.append(location.getLongitude() + "\n");
            sb.append("Radius:");
            sb.append(location.getRadius() + "\n");
            sb.append("Direc:");
            sb.append(location.getDirection() + "\n");
            sb.append("locTimes:");
            sb.append(locTimes + "\n");
            sb.append("Code:");
            sb.append(location.getLocType() + "\n");
            //检测POI获取结果
            if (location.getPoiList() == null || location.getPoiList().isEmpty()) {
                sb.append("POI为空！！！！！！！！！！！！！！！！！！");
            } else {
                for (int i = 0; i < location.getPoiList().size(); i++) {
                    Poi poi = (Poi) location.getPoiList().get(i);
                    sb.append("poiName:");
                    sb.append(poi.getName() + ", ");
                    sb.append("poiTag:");
                    sb.append(poi.getTags() + "\n");
                }
            }
            if (location.getPoiRegion() == null) {
                sb.append("Region为空！！！！！！！！！！！！！！！\n");
            } else {
                sb.append("PoiRegion ");// 返回定位位置相对poi的位置关系，仅在开发者设置需要POI信息时才会返回，在网络不通或无法获取时有可能返回null
                PoiRegion poiRegion = location.getPoiRegion();
                sb.append("DerectionDesc:"); // 获取POIREGION的位置关系
                sb.append(poiRegion.getDerectionDesc() + "; ");
                sb.append("Name:"); // 获取POIREGION的名字字符串
                sb.append(poiRegion.getName() + "; ");
                sb.append("Tags:"); // 获取POIREGION的类型
                sb.append(poiRegion.getTags() + "; ");
                sb.append("\nSDK版本: ");
            }

            //更新textview
//            updateMap(LocationResult, sb.toString());
        }
    };

    //    实现POI检索监听
    private OnGetPoiSearchResultListener poiListener = new OnGetPoiSearchResultListener() {
        @Override
        public void onGetPoiResult(final PoiResult result) {

            if (result == null || result.error == SearchResult.ERRORNO.RESULT_NOT_FOUND) {
                Toast.makeText(MainActivity.this, "以下再无信息", Toast.LENGTH_LONG).show();
                return;
            }

            StringBuffer sb1 = new StringBuffer(256);
            //POI检索结果地图标记形式
            mMap.clear();//清空地图标记
            if (result.error == SearchResult.ERRORNO.NO_ERROR) {
                sb1.append("\n这里成功啦！！！！！！！！！！\n");
                System.out.println("正在绘图！！！！！！！！！！！\n");
                //监听 View 绘制完成后获取view的高度
                mPoiDetailView.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
                    @Override
                    public void onGlobalLayout() {
                        int padding = 50;
                        // 添加poi
                        PoiOverlay overlay = new MyPoiOverlay(mMap);
                        mMap.setOnMarkerClickListener(overlay);
                        overlay.setData(result);
                        overlay.addToMap();
                        // 获取 view 的高度
                        int PaddingBootom = mPoiDetailView.getMeasuredHeight();
                        // 设置显示在规定宽高中的地图地理范围
                        overlay.zoomToSpanPaddingBounds(padding, padding, padding, PaddingBootom);
                        // 加载完后需要移除View的监听，否则会被多次触发
                        mPoiDetailView.getViewTreeObserver().removeOnGlobalLayoutListener(this);
                        showNearbyArea(ll, radius);//绘制检索范围（圆圈）
                    }
                });
            }

            //POI检索结果文字形式
            mAllPoi = result.getAllPoi();
            if (mAllPoi != null) {
                sb1.append("POI检索结果为:" + "\n");
                for (PoiInfo poiInfo : mAllPoi) {
                    sb1.append("Name:" + poiInfo.getName() + ", ");
                    sb1.append("Address:" + poiInfo.getAddress() + ", ");
                    sb1.append("Uid:" + poiInfo.getUid() + ", ");
                    sb1.append("Location:" + poiInfo.getLocation().latitude + "," + poiInfo.getLocation().longitude + "\n");
                }
            } else {
                sb1.append("POI检索失败！\n");
            }
            updateMap(mPoiResult, sb1.toString());
//            mPoiDetailView.setVisibility(View.VISIBLE);//显示文字结果

        }

        @Override
        public void onGetPoiDetailResult(PoiDetailResult poiDetailResult) {

        }

        @Override
        public void onGetPoiDetailResult(PoiDetailSearchResult poiDetailSearchResult) {

        }

        @Override
        public void onGetPoiIndoorResult(PoiIndoorResult poiIndoorResult) {

        }
    };

    private class MyPoiOverlay extends PoiOverlay {
        MyPoiOverlay(BaiduMap baiduMap) {
            super(baiduMap);
        }

        @Override
        public boolean onPoiClick(int index) {
            super.onPoiClick(index);
            PoiInfo poi = getPoiResult().getAllPoi().get(index);
            Toast.makeText(MainActivity.this, poi.address, Toast.LENGTH_LONG).show();
            return true;
        }
    }

    //开始检索
    public void searchNearby(int rad, int num) {
        KeybordUtil.closeKeybord(this);
        // 配置请求参数
        PoiNearbySearchOption nearbySearchOption = new PoiNearbySearchOption()
                .keyword("停车场") // 检索关键字
                .location(ll) // 经纬度
                .radius(rad) // 检索半径 单位： m
                .pageNum(num) // 分页编号
                .radiusLimit(false)
                .scope(2);
        // 发起检索
        poiSearchService.getPoiOb().searchNearby(nearbySearchOption);
        System.out.println(radius + " + " + num);
    }

    //更新textview
    public void updateMap(final TextView text, final String str) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                text.post(new Runnable() {
                    @Override
                    public void run() {
                        text.setText(str);
                    }
                });
            }
        }).start();
    }

    //对周边检索的范围进行绘制
    public void showNearbyArea(LatLng center, int radius) {
        OverlayOptions ooCircle = new CircleOptions()
                .fillColor(0x1033b5e5)
                .center(center)
                .stroke(new Stroke(2, 0xFFFF00FF))
                .radius(radius);
        mMap.addOverlay(ooCircle);
    }

    public static class KeybordUtil {

        //关闭软键盘
        public static void closeKeybord(Activity activity) {
            InputMethodManager imm = (InputMethodManager) activity.getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) {
                imm.hideSoftInputFromWindow(activity.getWindow().getDecorView().getWindowToken(), 0);
            }
        }
    }
}