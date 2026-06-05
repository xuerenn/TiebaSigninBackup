package top.srcrs;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import top.srcrs.domain.Cookie;
import top.srcrs.util.Encryption;
import top.srcrs.util.Request;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;


/*
 * 这里就是程序运行开始的地方
 * @author srcrs
 * @Time 2020-10-31
 */
public class Run {

    /** 获取用户关注的所有贴吧 */
    String LIKE_URL = "https://tieba.baidu.com/mo/q/newmoindex";
    /** 获取用户的 tbs */
    String TBS_URL = "http://tieba.baidu.com/dc/common/tbs";
    /** 贴吧签到接口 */
    String SIGN_URL = "http://c.tieba.baidu.com/c/c/forum/sign";

    /** 存储用户关注的所有贴吧 */
    private List<String> follow = new ArrayList<>();
    /** 签到成功的贴吧列表 */
    private static List<String> success = new ArrayList<>();
    /** 用户的tbs */
    private String tbs = "";
    /** 用户所关注的贴吧数量 */
    private static Integer followNum = 201;

    private static String logMessageString = new String();

    public static void main(String[] args) {

        Cookie cookie = Cookie.getInstance();
        // 存入Cookie，以备使用
        if (args.length == 0) {
            logMessageString += getLogMessage("请在Secrets中填写BDUSS\n");
            return;
        }
        cookie.setBDUSS(args[0]);
        Run run = new Run();
        run.getTbs();
        run.getFollow();
        run.runSign();
        logMessageString += getLogMessage(
                String.format("共 %d 个贴吧 - 成功: %d - 失败: %d", followNum, success.size(), followNum - success.size()));
        save(logMessageString);
    }

    /*
     * 进行登录，获得 tbs ，签到的时候需要用到这个参数
     * 
     * @author srcrs
     * 
     * @Time 2020-10-31
     */
    public void getTbs() {
        try {
            JSONObject jsonObject = Request.get(TBS_URL);
            if ("1".equals(jsonObject.getString("is_login"))) {
                logMessageString += getLogMessage("获取tbs成功");
                tbs = jsonObject.getString("tbs");
            } else {
                logMessageString += getLogMessage("获取tbs失败 -- " + jsonObject);
            }
        } catch (Exception e) {
            logMessageString += getLogMessage("获取tbs部分出现错误 -- " + e);
        }
    }

    /**
     * 获取用户所关注的贴吧列表
     * 
     * @author srcrs
     * @Time 2020-10-31
     */
    public void getFollow() {
        try {
            JSONObject jsonObject = Request.get(LIKE_URL);
            logMessageString += getLogMessage("获取贴吧列表成功\n");
            JSONArray jsonArray = jsonObject.getJSONObject("data").getJSONArray("like_forum");
            followNum = jsonArray.size();
            // 获取用户所有关注的贴吧
            for (Object array : jsonArray) {
                if ("0".equals(((JSONObject) array).getString("is_sign"))) {
                    // 将为签到的贴吧加入到 follow 中，待签到
                    follow.add(((JSONObject) array).getString("forum_name").replace("+", "%2B"));
                } else {
                    // 将已经成功签到的贴吧，加入到 success
                    success.add(((JSONObject) array).getString("forum_name"));
                }
            }
        } catch (Exception e) {
            logMessageString += getLogMessage("获取贴吧列表部分出现错误 -- " + e + "\n");
        }
    }

    /**
     * 开始进行签到，每一轮性将所有未签到的贴吧进行签到，一共进行5轮，如果还未签到完就立即结束
     * 一般一次只会有少数的贴吧未能完成签到，为了减少接口访问次数，每一轮签到完等待1分钟，如果在过程中所有贴吧签到完则结束。
     * 
     * @author srcrs
     * @Time 2020-10-31
     */
    public void runSign() {
        // 当执行 5 轮，所有贴吧还未签到成功就结束操作
        Integer flag = 5;
        try {
            while (success.size() < followNum && flag > 0) {
                logMessageString += getLogMessage(String.format("-----第 %d 轮签到开始-----", 5 - flag + 1));
                logMessageString += getLogMessage(String.format("还剩 %d 贴吧需要签到", followNum - success.size()));
                Iterator<String> iterator = follow.iterator();
                while (iterator.hasNext()) {
                    String s = iterator.next();
                    String rotation = s.replace("%2B", "+");
                    String body = "kw=" + s + "&tbs=" + tbs + "&sign="
                            + Encryption.enCodeMd5("kw=" + rotation + "tbs=" + tbs + "tiebaclient!!!");
                    JSONObject post = Request.post(SIGN_URL, body);
                    if ("0".equals(post.getString("error_code"))) {
                        iterator.remove();
                        success.add(rotation);
                        logMessageString += getLogMessage(rotation + ": " + "签到成功");
                    } else {
                        logMessageString += getLogMessage(rotation + ": " + "签到失败");
                    }
                }
                if (success.size() != followNum) {
                    // 为防止短时间内多次请求接口，触发风控，设置每一轮签到完等待 5 分钟
                    Thread.sleep(1000 * 60 * 5);
                    /**
                     * 重新获取 tbs
                     * 尝试解决以前第 1 次签到失败，剩余 4 次循环都会失败的错误。
                     */
                    getTbs();
                }
                flag--;
            }
        } catch (Exception e) {
            logMessageString += getLogMessage("签到部分出现错误 -- " + e);
        }
    }

    /**
     * 保存日志文件
     * 
     * @param logMessage
     * @author srcrs
     * @Time 2020-10-31
     */
    public static void save(String logMessage) {

        // 把logMessage写入文件，方便查看，如果文件存在则直接覆盖
        try {
            OutputStreamWriter writer = new OutputStreamWriter(new FileOutputStream("TiebaSignIn.txt"), "UTF-8");
            writer.write(logMessage);
            writer.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static String getLogMessage(String message) {
        ZonedDateTime zdf = ZonedDateTime.now();
        String sDate = zdf.format(DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss"));
        return "[" + sDate + "]: " + message + "\n";
    }

}
