package com.hippo.ehviewer.client.parser;

import android.util.Log;

import com.google.firebase.crashlytics.FirebaseCrashlytics;
import com.hippo.ehviewer.client.data.HomeDetail;
import com.hippo.ehviewer.client.exception.EhException;
import com.hippo.lib.yorozuya.NumberUtils;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class EhHomeParser {
    private static final String TAG = "EhHomeParser";
    private static final Pattern PATTERN_IMAGE_LIMIT = Pattern.compile("<p>You are currently at <strong>(\\d+)</strong> towards a limit of <strong>(\\d+)</strong>.</p>.+?<p>Reset Cost: <strong>(\\d+)</strong> GP</p>", Pattern.DOTALL);
    private static final Pattern PATTERN_IMAGE_LIMIT_NEW = Pattern.compile("<p>You are currently at <strong>(.+?)</strong> towards your account limit of <strong>(.+?)</strong>.</p>\n<p>You can reset your image quota by spending <strong>(.+?)</strong> GP.</p>", Pattern.DOTALL);

    private static final String HOME_BOX = "homebox";


    /**
     * 个人详情页数据处理
     *
     * @param body 传入原始个人中心html数据
     * @return 返回从html数据中提取的有用数据
     */
    public static HomeDetail parse(String body) throws EhException {

        HomeDetail homeDetail = new HomeDetail();

        try {
            Document document = Jsoup.parse(body);
            Elements homeBoxes = document.getElementsByClass(HOME_BOX);

            if (homeBoxes.isEmpty()) {
                return homeDetail;
            }
            Element imageLimits = homeBoxes.get(0);
            parseImageLimits(imageLimits, homeDetail);
            Element totalGpGained = homeBoxes.get(2);
            parseTotalGpGained(totalGpGained, homeDetail);
            Element moderationPower = homeBoxes.get(4);
            parseModerationPower(moderationPower, homeDetail);
        } catch (Exception e ) {
            Log.e(TAG, "数据结构错误");
            FirebaseCrashlytics.getInstance().recordException(e);
        }

        return homeDetail;
    }

    private static void parseModerationPower(Element moderationPower, HomeDetail homeDetail) {
        Element tableRow = moderationPower.getAllElements().get(0).child(0);
        Elements rows = tableRow.children();
        long power = NumberUtils.parseLongSafely(rows.get(0).child(0).child(0)
                .child(1).text().replaceAll(",",""), -1L);
        homeDetail.setCurrentModerationPower(power);
    }

    private static void parseTotalGpGained(Element totalGpGained, HomeDetail homeDetail) {
        Element table = totalGpGained.getAllElements().get(0);
        Element rows = table.child(0).child(0);
        long galleryVisits = NumberUtils.parseLongSafely(rows.child(0).child(0).text()
                .replaceAll(",",""), -1L);
        long torrentCompletions = NumberUtils.parseLongSafely(rows.child(1).child(0).text()
                .replaceAll(",",""), -1L);
        long archiveDownloads = NumberUtils.parseLongSafely(rows.child(2).child(0).text()
                .replaceAll(",",""), -1L);
        long hentaiAtHome = NumberUtils.parseLongSafely(rows.child(3).child(0).text()
                .replaceAll(",",""), -1L);

        homeDetail.setFromGalleryVisits(galleryVisits);
        homeDetail.setFromTorrentCompletions(torrentCompletions);
        homeDetail.setFromArchiveDownloads(archiveDownloads);
        homeDetail.setFromHentaiAtHome(hentaiAtHome);
    }

    private static void parseImageLimits(Element imageLimits, HomeDetail homeDetail) {
        String imageListsString = imageLimits.html();
        Matcher matcher = PATTERN_IMAGE_LIMIT_NEW.matcher(imageListsString);
        if (!matcher.find()) {
            matcher = PATTERN_IMAGE_LIMIT.matcher(imageListsString);
            if (!matcher.find()){
                return;
            }
        }

        long used = NumberUtils.parseLongSafely(getGroupIntString(1,matcher), -1L);
        long total = NumberUtils.parseLongSafely(getGroupIntString(2,matcher), -1L);
        long resetCost = NumberUtils.parseLongSafely(getGroupIntString(3,matcher), -1L);
        homeDetail.setUsed(used);
        homeDetail.setTotal(total);
        homeDetail.setResetCost(resetCost);
    }

    private static String getGroupIntString(int index,Matcher matcher){
        String groupS = matcher.group(index);
        if (groupS!=null){
            return groupS.replaceAll(",","");
        }
        return "0";
    }

}
