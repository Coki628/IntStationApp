package mc.arct.intstationapp.network;

import android.app.Activity;
import android.app.DownloadManager;
import android.app.ProgressDialog;
import android.app.VoiceInteractor;
import android.os.AsyncTask;

import com.squareup.okhttp.OkHttpClient;
import com.squareup.okhttp.Request;
import com.squareup.okhttp.Response;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.IOException;
import java.util.ArrayList;

import mc.arct.intstationapp.models.StationDetailVO;
import mc.arct.intstationapp.models.StationTransferVO;
import mc.arct.intstationapp.utils.ConvertUtil;

/**
 * Jorudanから情報を取得する非同期処理クラス
 */

public class JorudanInfoTask extends AsyncTask< Void, Integer, String[]> {
    // doInBackgroundメソッドの引数の型, onProgressUpdateメソッドの引数の型, onPostExecuteメソッドの戻り値の型

    // 結果格納用リストをまとめた配列
    private ArrayList<StationTransferVO>[] resultInfoLists;
    // 出発駅用配列
    private StationDetailVO[] inputStationArray;
    // 到着駅
    private String destStation;
    // 検索URL格納用配列
    private String[] searchURLArray;
    // プログレスバー
    private Activity activity;
    private ProgressDialog dialog;
    // コールバック用
    private CallBackTask callbacktask;

    /**
     * コンストラクタ(onPostExecuteで更新したいViewなどを渡す)
     *
     * @param activity プログレスバーに渡すアクティビティ
     * @param inputStationList 入力された駅名のリスト
     * @param destStationName 到着候補の駅名(ジョルダンでの駅名)
     */
    public JorudanInfoTask(Activity activity, ArrayList<StationDetailVO> inputStationList,
                           String destStationName) {
        super();
        this.resultInfoLists = new ArrayList[inputStationList.size()];
        this.inputStationArray =
                inputStationList.toArray(new StationDetailVO[inputStationList.size()]);
        this.destStation = destStationName;
        this.activity = activity;
        this.searchURLArray = new String[inputStationList.size()];
    }

    // 非同期処理前にUIスレッドで実行される
    @Override
    protected void onPreExecute() {
        // プログレスバーで進捗を表示
        dialog = new ProgressDialog(this.activity);
        dialog.setTitle("");
        dialog.setMessage("");
        dialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
        dialog.setCancelable(false);
        dialog.setMax(inputStationArray.length);
        dialog.setProgress(0);
        dialog.show();
    }

    // 非同期処理
    @Override
    protected String[] doInBackground(Void... prams) {

        // 結果格納用配列
        String[] resultArray = new String[inputStationArray.length];

        try {
            for (int i = 0; i < inputStationArray.length; i++) {
                // 検索URLを格納
                searchURLArray[i] = "https://www.jorudan.co.jp/norikae/cgi/nori.cgi?Sok=決+定&eki1="
                        + inputStationArray[i].getJorudanName() + "&eki2=" + destStation;
                // 入力された駅の数だけ検索の通信をする
                Request request = new Request.Builder()
                        .url(searchURLArray[i])
                        .get()
                        .build();

                OkHttpClient client = new OkHttpClient();

                Response response = client.newCall(request).execute();
                // 受け取ったHTMLソースの文字列を結果格納用配列に詰める
                resultArray[i] = response.body().string();
                // プログレスバーの更新
                publishProgress(i + 1);
            }
        } catch (IOException e) {

            e.printStackTrace();
        }
        return resultArray;
    }

    // publishProgress実行時に呼ばれる
    @Override
    protected void onProgressUpdate(Integer... values) {
        // プログレスバーの更新
        dialog.setProgress(values[0]);
    }

    // 結果に応じた処理をUIスレッドで行う
    @Override
    protected void onPostExecute(String[] resultArray) {
        super.onPostExecute(resultArray);
        // 検索結果HTMLの数(=出発駅の数)だけ繰り返す
        for (int i = 0; i < resultArray.length; i++) {
            // 1～○番目までの候補経路セットを格納するリスト
            resultInfoLists[i] = new ArrayList<>();
            // 取得したwebページから必要な各情報を取得
            Document doc = Jsoup.parse(resultArray[i]);
            // 例外的に対応する必要のあるケースを先に処理
            // 近すぎる検索の場合(例：有楽町～日比谷)
            if (doc.getElementById("search_msg").text().equals("検索できない駅の指定です。（近距離です。）")) {
                // 結果格納用VO
                StationTransferVO vo =
                        new StationTransferVO(inputStationArray[i].getJorudanName(), destStation, searchURLArray[i]);
                resultInfoLists[i].add(vo);
            // 同じ駅を検索した場合
            } else if (doc.getElementById("search_msg").text().equals("出発地 到着地 に同じ目的地は設定できません。")) {
                // 結果格納用VO
                StationTransferVO vo =
                        new StationTransferVO(inputStationArray[i].getJorudanName(), destStation, searchURLArray[i]);
                resultInfoLists[i].add(vo);
            // 通常の検索結果が得られた場合
            } else {
                // 下記のtbodyタグ配下のtrタグは候補経路の数だけあるので、これで判断する
                Element tBody = doc.getElementById("Bk_list_tbody");
                for (int j = 0; j < tBody.getElementsByTag("tr").size(); j++) {
                    // 結果格納用VO
                    StationTransferVO vo =
                            new StationTransferVO(inputStationArray[i].getJorudanName(), destStation, searchURLArray[i]);
                    // 必要なタグから情報を受け取る
                    String timeStr = tBody.child(j).child(2).text();
                    String costStr = tBody.child(j).child(4).text();
                    String transferStr = tBody.child(j).child(3).text();
                    // 整形処理("○分","○円","乗換 ○回"を数値部分のみにする)をしてVOにセット
                    vo.setTime(ConvertUtil.timeToMinutes(timeStr));
                    vo.setCost(ConvertUtil.removeYenAndComma(costStr));
                    vo.setTransfer(ConvertUtil.removeNorikaeAndKai(transferStr));
                    resultInfoLists[i].add(vo);
                }
                // 経路の途中駅を格納する
                Elements routes = doc.getElementsByClass("route");
                //
                for (int j = 0; j < routes.size(); j++) {
                    // このループは候補経路の数だけ回る
                    Elements stations = routes.get(j).getElementsByClass("nm");
                    // 一つの候補経路に含まれる駅名
                    ArrayList<String> transferList = new ArrayList<>();
                    // 候補経路に含まれる駅名を格納するリスト
                    for (Element station : stations) {
                        transferList.add(station.text());
                    }
                    // i番目の出発駅のj番目の候補経路に含まれる駅名リストの格納
                    resultInfoLists[i].get(j).setTransferList(transferList);
                }
            }
        }
        // プログレスバーを終了する
        dialog.dismiss();
        // コールバックでUIスレッドに非同期処理の終了を伝える
        callbacktask.CallBack();
    }

    public ArrayList<StationTransferVO>[] getResultInfoLists() {

        return this.resultInfoLists;
    }

    public void setOnCallBack(CallBackTask _cbj) {
        callbacktask = _cbj;
    }

    /**
     * コールバック用のstaticなclass
     */
    public static class CallBackTask {
        // resultにはdoInBackgroundの戻り値(onPostExecuteの引数)が入る
        public void CallBack(String[] result) {
        }
        // 特に何も受け取らない時はこっちでいい
        public void CallBack() {
        }
    }
}