package mc.arct.intstationapp.models;

/**
 * 駅間距離情報VO
 *
 * 2駅間の距離を保持する。
 */

public class StationDistanceVO extends StationVO {

    private double distance;

    // 駅名と距離を使うコンストラクタ
    public StationDistanceVO (String name, String kana, double distance) {

        super(name, kana);
        this.distance = distance;
    }

    public double getDistance() {
        return distance;
    }

    @Override
    public String toString() {
        return super.toString() + "\n" +
                "StationDistanceVO{" +
                "distance=" + distance +
                '}';
    }
}
