package aoharureverie.ocaacrclient.oldchat.ui;

import android.os.Bundle;
import android.view.View;
import android.widget.ScrollView;
import android.widget.TextView;
import aoharureverie.ocaacrclient.oldchat.BaseActivity;
import aoharureverie.ocaacrclient.oldchat.R;

public class PrivacyPolicyActivity extends BaseActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_privacy_policy);

        View btnBack = findViewByIdCompat(R.id.btnPrivacyBack);
        if (btnBack != null) {
            btnBack.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    finish();
                }
            });
        }

        TextView tvContent = (TextView) findViewByIdCompat(R.id.tvPrivacyContent);
        if (tvContent != null) {
            tvContent.setText(getPrivacyPolicyText());
        }
    }

    private String getPrivacyPolicyText() {
        return "旧聊隐私协议\n\n" +
                "更新日期：2026年2月6日\n" +
                "生效日期：2026年2月6日\n\n" +
                "欢迎使用旧聊！\n\n" +
                "我们非常重视您的隐私保护和个人信息安全。本隐私协议旨在向您说明我们如何收集、使用、存储和保护您的个人信息。\n\n" +
                "一、信息收集\n\n" +
                "1.1 账号信息\n" +
                "当您注册旧聊账号时，我们会收集您提供的以下信息：\n" +
                "- 用户名\n" +
                "- 密码（加密存储）\n" +
                "- 电子邮箱（用于账号验证和找回密码）\n\n" +
                "1.2 个人资料\n" +
                "您可以选择性地提供以下信息：\n" +
                "- 头像\n" +
                "- 昵称\n" +
                "- 个性签名\n" +
                "- 封面图片\n\n" +
                "1.3 使用信息\n" +
                "为了提供更好的服务，我们会收集以下信息：\n" +
                "- 设备信息（设备型号、操作系统版本）\n" +
                "- 日志信息（应用崩溃日志、错误日志）\n" +
                "- 使用统计（仅用于改进应用性能）\n\n" +
                "1.4 通讯内容\n" +
                "为了提供即时通讯服务，我们会存储：\n" +
                "- 聊天消息\n" +
                "- 发送的图片、语音、文件\n" +
                "- 群组信息\n\n" +
                "二、信息使用\n\n" +
                "2.1 我们收集的信息仅用于以下目的：\n" +
                "- 提供即时通讯服务\n" +
                "- 维护账号安全\n" +
                "- 改进应用功能和性能\n" +
                "- 处理用户反馈和技术支持\n\n" +
                "2.2 我们承诺：\n" +
                "- 不会将您的个人信息出售给第三方\n" +
                "- 不会将您的聊天内容用于广告推送\n" +
                "- 不会在未经您同意的情况下公开您的个人信息\n\n" +
                "三、信息存储\n\n" +
                "3.1 存储位置\n" +
                "您的信息存储在我们的服务器上，我们采取合理的安全措施保护您的数据。\n\n" +
                "3.2 存储期限\n" +
                "- 账号信息：在您注销账号前一直保存\n" +
                "- 聊天记录：根据您的设置保存在本地设备\n" +
                "- 日志信息：保存30天后自动删除\n\n" +
                "四、信息安全\n\n" +
                "4.1 我们采取以下措施保护您的信息：\n" +
                "- 密码加密存储\n" +
                "- 访问权限控制\n" +
                "- 定期安全审计\n\n" +
                "4.2 尽管我们采取了合理的安全措施，但请您理解，互联网环境下不存在绝对的安全。\n\n" +
                "五、您的权利\n\n" +
                "5.1 您有权：\n" +
                "- 访问和修改您的个人资料\n" +
                "- 删除您的聊天记录\n" +
                "- 注销您的账号\n" +
                "- 导出您的数据\n\n" +
                "5.2 如需行使上述权利，请通过应用内的反馈功能联系我们。\n\n" +
                "六、未成年人保护\n\n" +
                "我们非常重视未成年人的隐私保护。如果您是未成年人，请在监护人的陪同下使用本应用。\n\n" +
                "七、第三方服务\n\n" +
                "本应用可能包含第三方服务的链接。我们不对第三方的隐私政策负责，请您在使用第三方服务前仔细阅读其隐私政策。\n\n" +
                "八、隐私协议的变更\n\n" +
                "我们可能会不时更新本隐私协议。重大变更时，我们会通过应用内通知的方式告知您。\n\n" +
                "九、联系我们\n\n" +
                "如果您对本隐私协议有任何疑问或建议，请通过以下方式联系我们：\n" +
                "- 应用内反馈功能\n" +
                "- 邮箱：oldchat@163.com\n\n" +
                "感谢您信任并使用旧聊！";
    }
}
