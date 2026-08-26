package xyz.erupt.talent.service;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import xyz.erupt.core.config.GsonFactory;
import xyz.erupt.talent.model.TalentSetting;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * DingTalk calendar integration, configured visually in「招聘系统配置」.
 * <p>
 * Two token flavours are needed because DingTalk splits its surface: the legacy {@code oapi}
 * host resolves a phone number to a userId, while the {@code v1.0} host owns the calendar.
 * Both are derived from the same AppKey / AppSecret pair.
 *
 * @author YuePeng
 */
@Slf4j
@Service
public class DingTalkService {

    private static final String OLD_TOKEN_URL = "https://oapi.dingtalk.com/gettoken?appkey=%s&appsecret=%s";

    private static final String NEW_TOKEN_URL = "https://api.dingtalk.com/v1.0/oauth2/accessToken";

    private static final String USER_BY_MOBILE_URL = "https://oapi.dingtalk.com/topapi/v2/user/getbymobile?access_token=%s";

    private static final String CREATE_EVENT_URL = "https://api.dingtalk.com/v1.0/calendar/users/%s/calendars/primary/events";

    private static final String DELETE_EVENT_URL = "https://api.dingtalk.com/v1.0/calendar/users/%s/calendars/primary/events/%s";

    private static final String WORK_NOTICE_URL = "https://oapi.dingtalk.com/topapi/message/corpconversation/asyncsend_v2?access_token=%s";

    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");

    private static final DateTimeFormatter RFC3339 = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssXXX");

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    /**
     * appKey -> cached token. Tokens live 7200s upstream; refreshed a little early.
     */
    private final Map<String, CachedToken> tokenCache = new ConcurrentHashMap<>();

    @Resource
    private TalentSettingService settingService;

    /**
     * Verifies the credentials by fetching a token. Used by the "test credentials" button.
     */
    public void testCredentials(TalentSetting setting) throws Exception {
        requireConfig(setting);
        this.newToken(setting);
        this.oldToken(setting);
    }

    /**
     * Creates or re-creates the interview event on the interviewer's primary DingTalk calendar.
     * <p>
     * A reschedule is handled as delete-old-then-create-new rather than an in-place update: it keeps
     * the flow to the two endpoints we rely on elsewhere, and it naturally moves the event to a new
     * owner when the interviewer changes (the owner is a path parameter, not an editable field).
     *
     * @param existingEventId     the previously synced event id, or blank for a first-time sync
     * @param existingOwnerUserId the owner of the previously synced event, used to delete it
     * @param attendeeMobiles     phone numbers of everyone to invite; the first one owns the calendar
     * @return the freshly created event together with its calendar owner
     */
    public CalendarEvent upsertInterviewEvent(String existingEventId, String existingOwnerUserId,
                                              String summary, String description, String location,
                                              ZonedDateTime start, int durationMinutes,
                                              List<String> attendeeMobiles) throws Exception {
        TalentSetting setting = settingService.get();
        if (!Boolean.TRUE.equals(setting.getDingEnabled())) {
            throw new IllegalStateException("钉钉日程同步未启用，请在「招聘系统配置」中开启");
        }
        requireConfig(setting);
        if (StringUtils.isNoneBlank(existingEventId, existingOwnerUserId)) {
            // Best-effort: a stale event upstream is better than aborting the reschedule entirely.
            try {
                this.deleteEvent(setting, existingOwnerUserId, existingEventId);
            } catch (Exception e) {
                log.warn("钉钉旧日程删除失败，继续创建新日程 eventId={}: {}", existingEventId, e.getMessage());
            }
        }
        String oldToken = this.oldToken(setting);
        List<String> userIds = attendeeMobiles.stream()
                .filter(StringUtils::isNotBlank)
                .map(mobile -> this.userIdByMobile(oldToken, mobile))
                .filter(StringUtils::isNotBlank)
                .distinct()
                .toList();
        if (userIds.isEmpty()) {
            throw new IllegalStateException("未能解析出任何钉钉用户，请确认面试官与 HR 的手机号已在系统中维护且已加入钉钉组织");
        }

        JsonObject body = this.buildEventBody(summary, description, location, start, durationMinutes, userIds);
        HttpRequest request = HttpRequest.newBuilder(URI.create(String.format(CREATE_EVENT_URL, userIds.get(0))))
                .timeout(Duration.ofSeconds(20))
                .header("Content-Type", "application/json")
                .header("x-acs-dingtalk-access-token", this.newToken(setting))
                .POST(HttpRequest.BodyPublishers.ofString(body.toString(), StandardCharsets.UTF_8))
                .build();
        JsonObject result = this.exchange(request);
        if (!result.has("id")) {
            throw new IllegalStateException("钉钉未返回日程 ID：" + result);
        }
        return new CalendarEvent(result.get("id").getAsString(), userIds.get(0));
    }

    /**
     * Removes a previously synced event, e.g. when the interview record is deleted. No-op when
     * DingTalk is disabled or the record was never synced.
     */
    public void deleteInterviewEvent(String ownerUserId, String eventId) throws Exception {
        if (StringUtils.isAnyBlank(ownerUserId, eventId)) {
            return;
        }
        TalentSetting setting = settingService.get();
        if (!Boolean.TRUE.equals(setting.getDingEnabled())) {
            return;
        }
        requireConfig(setting);
        this.deleteEvent(setting, ownerUserId, eventId);
    }

    /**
     * Pushes a DingTalk work notification (工作通知) to the given people. Best-effort: silently skips
     * when DingTalk or the AgentId is not configured, and never throws so it cannot break the caller.
     *
     * @param mobiles phone numbers of the recipients, resolved to DingTalk userIds
     */
    public void sendWorkNotice(List<String> mobiles, String title, String text) {
        try {
            TalentSetting setting = settingService.get();
            if (!Boolean.TRUE.equals(setting.getDingEnabled()) || StringUtils.isBlank(setting.getDingAgentId())) {
                return;
            }
            requireConfig(setting);
            String oldToken = this.oldToken(setting);
            List<String> userIds = mobiles.stream()
                    .filter(StringUtils::isNotBlank)
                    .map(mobile -> this.userIdByMobile(oldToken, mobile))
                    .filter(StringUtils::isNotBlank)
                    .distinct()
                    .toList();
            if (userIds.isEmpty()) {
                return;
            }
            JsonObject markdown = new JsonObject();
            markdown.addProperty("title", title);
            markdown.addProperty("text", "#### " + title + "\n\n" + text);
            JsonObject msg = new JsonObject();
            msg.addProperty("msgtype", "markdown");
            msg.add("markdown", markdown);
            JsonObject body = new JsonObject();
            try {
                body.addProperty("agent_id", Long.parseLong(setting.getDingAgentId().trim()));
            } catch (NumberFormatException e) {
                body.addProperty("agent_id", setting.getDingAgentId().trim());
            }
            body.addProperty("userid_list", String.join(",", userIds));
            body.add("msg", msg);
            HttpRequest request = HttpRequest.newBuilder(URI.create(String.format(WORK_NOTICE_URL, oldToken)))
                    .timeout(Duration.ofSeconds(15))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body.toString(), StandardCharsets.UTF_8))
                    .build();
            JsonObject response = this.exchange(request);
            if (response.has("errcode") && response.get("errcode").getAsInt() != 0) {
                log.warn("钉钉工作通知发送失败 errcode={} errmsg={}",
                        response.get("errcode").getAsInt(),
                        response.has("errmsg") ? response.get("errmsg").getAsString() : "");
            }
        } catch (Exception e) {
            log.warn("钉钉工作通知发送异常: {}", e.getMessage());
        }
    }

    private JsonObject buildEventBody(String summary, String description, String location,
                                      ZonedDateTime start, int durationMinutes, List<String> userIds) {
        JsonObject body = new JsonObject();
        body.addProperty("summary", summary);
        body.addProperty("description", description);
        body.add("start", timePoint(start));
        body.add("end", timePoint(start.plusMinutes(durationMinutes)));
        if (StringUtils.isNotBlank(location)) {
            JsonObject loc = new JsonObject();
            loc.addProperty("displayName", location);
            body.add("location", loc);
        }
        JsonArray attendees = new JsonArray();
        // The calendar owner is userIds[0]; everyone else is invited.
        for (int i = 1; i < userIds.size(); i++) {
            JsonObject attendee = new JsonObject();
            attendee.addProperty("id", userIds.get(i));
            attendees.add(attendee);
        }
        if (!attendees.isEmpty()) {
            body.add("attendees", attendees);
        }
        JsonArray reminders = new JsonArray();
        JsonObject reminder = new JsonObject();
        reminder.addProperty("method", "app");
        reminder.addProperty("minutes", "15");
        reminders.add(reminder);
        body.add("reminders", reminders);
        return body;
    }

    private void deleteEvent(TalentSetting setting, String ownerUserId, String eventId) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create(String.format(DELETE_EVENT_URL, ownerUserId, eventId)))
                .timeout(Duration.ofSeconds(15))
                .header("x-acs-dingtalk-access-token", this.newToken(setting))
                .DELETE()
                .build();
        // The delete endpoint answers 204 with an empty body, so status is the only signal.
        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() >= 400) {
            throw new IllegalStateException("钉钉删除日程失败 HTTP " + response.statusCode() + "："
                    + StringUtils.defaultIfBlank(response.body(), ""));
        }
    }

    private JsonObject timePoint(ZonedDateTime time) {
        JsonObject point = new JsonObject();
        point.addProperty("dateTime", time.withZoneSameInstant(ZONE).format(RFC3339));
        point.addProperty("timeZone", ZONE.getId());
        return point;
    }

    private String userIdByMobile(String oldToken, String mobile) {
        try {
            JsonObject body = new JsonObject();
            body.addProperty("mobile", mobile);
            HttpRequest request = HttpRequest.newBuilder(URI.create(String.format(USER_BY_MOBILE_URL, oldToken)))
                    .timeout(Duration.ofSeconds(15))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body.toString(), StandardCharsets.UTF_8))
                    .build();
            JsonObject response = this.exchange(request);
            if (response.has("errcode") && response.get("errcode").getAsInt() != 0) {
                log.warn("钉钉手机号解析失败 errcode={} errmsg={}",
                        response.get("errcode").getAsInt(),
                        response.has("errmsg") ? response.get("errmsg").getAsString() : "");
                return null;
            }
            JsonObject result = response.getAsJsonObject("result");
            return null == result || !result.has("userid") ? null : result.get("userid").getAsString();
        } catch (Exception e) {
            log.warn("钉钉手机号解析异常: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Token for the {@code api.dingtalk.com/v1.0} surface (calendar).
     */
    private String newToken(TalentSetting setting) throws Exception {
        return cached("new:" + setting.getDingAppKey(), () -> {
            JsonObject body = new JsonObject();
            body.addProperty("appKey", setting.getDingAppKey());
            body.addProperty("appSecret", setting.getDingAppSecret());
            HttpRequest request = HttpRequest.newBuilder(URI.create(NEW_TOKEN_URL))
                    .timeout(Duration.ofSeconds(15))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body.toString(), StandardCharsets.UTF_8))
                    .build();
            JsonObject response = this.exchange(request);
            if (!response.has("accessToken")) {
                throw new IllegalStateException("钉钉凭据无效：" + response);
            }
            return response.get("accessToken").getAsString();
        });
    }

    /**
     * Token for the legacy {@code oapi.dingtalk.com} surface (contacts).
     */
    private String oldToken(TalentSetting setting) throws Exception {
        return cached("old:" + setting.getDingAppKey(), () -> {
            HttpRequest request = HttpRequest.newBuilder(
                            URI.create(String.format(OLD_TOKEN_URL, setting.getDingAppKey(), setting.getDingAppSecret())))
                    .timeout(Duration.ofSeconds(15))
                    .GET()
                    .build();
            JsonObject response = this.exchange(request);
            if (!response.has("access_token")) {
                throw new IllegalStateException("钉钉凭据无效：" + response);
            }
            return response.get("access_token").getAsString();
        });
    }

    private JsonObject exchange(HttpRequest request) throws Exception {
        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        JsonObject json;
        try {
            json = GsonFactory.getGson().fromJson(response.body(), JsonObject.class);
        } catch (Exception e) {
            throw new IllegalStateException("钉钉返回非 JSON 响应，HTTP " + response.statusCode());
        }
        if (null == json) {
            throw new IllegalStateException("钉钉返回空响应，HTTP " + response.statusCode());
        }
        if (response.statusCode() >= 400) {
            String message = json.has("message") ? json.get("message").getAsString() : json.toString();
            throw new IllegalStateException("钉钉接口错误 HTTP " + response.statusCode() + "：" + message);
        }
        return json;
    }

    private String cached(String key, TokenLoader loader) throws Exception {
        CachedToken cached = tokenCache.get(key);
        if (null != cached && cached.expireAt > System.currentTimeMillis()) {
            return cached.token;
        }
        String token = loader.load();
        // DingTalk tokens last 7200s; refresh 5 minutes early to stay clear of the boundary.
        tokenCache.put(key, new CachedToken(token, System.currentTimeMillis() + Duration.ofSeconds(6900).toMillis()));
        return token;
    }

    private void requireConfig(TalentSetting setting) {
        if (StringUtils.isAnyBlank(setting.getDingAppKey(), setting.getDingAppSecret())) {
            throw new IllegalStateException("钉钉配置不完整：AppKey 与 AppSecret 均为必填");
        }
    }

    private interface TokenLoader {
        String load() throws Exception;
    }

    private record CachedToken(String token, long expireAt) {
    }

    /**
     * A synced calendar event plus the DingTalk userId that owns it.
     */
    public record CalendarEvent(String eventId, String ownerUserId) {
    }

}
