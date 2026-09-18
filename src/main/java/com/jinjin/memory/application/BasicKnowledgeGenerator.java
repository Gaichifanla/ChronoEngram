package com.jinjin.memory.application;

public class BasicKnowledgeGenerator {
    private static final String[] SUBJECTS = {
            "饮水", "睡眠", "运动", "早餐", "蔬菜", "水果", "牙齿", "洗手", "厨房", "冰箱",
            "雨天", "出门", "手机", "电脑", "阅读", "学习", "记账", "时间管理", "交通", "安全",
            "感冒", "用药", "眼睛", "坐姿", "沟通", "会议", "购物", "收纳", "清洁", "旅行"
    };

    private static final String[] FACTS = {
            "保持规律习惯通常比偶尔高强度行动更容易坚持。",
            "提前准备清单可以减少遗漏和临时决策成本。",
            "遇到不确定情况时，先确认信息来源会更可靠。",
            "适度记录过程有助于之后复盘和查找原因。",
            "把复杂任务拆成小步骤，执行起来通常更轻松。",
            "定期检查状态可以更早发现问题。",
            "环境越整洁，寻找物品和恢复工作的成本越低。",
            "重要事项最好留下明确时间、地点和负责人。",
            "长期目标需要配合短期反馈，才更容易调整方向。",
            "当信息很多时，分类和标签能显著提高查找效率。"
    };

    private static final String[] SCENARIOS = {
            "家庭生活", "个人健康", "日常工作", "学习计划", "出行准备",
            "数字设备", "财务管理", "人际沟通", "安全习惯", "物品管理"
    };

    private BasicKnowledgeGenerator() {
    }

    public static String content(int index) {
        String subject = SUBJECTS[index % SUBJECTS.length];
        String fact = FACTS[index % FACTS.length];
        String scenario = SCENARIOS[index % SCENARIOS.length];
        int day = index % 30 + 1;
        return "生活常识#" + index + "：" + scenario + "中关于" + subject + "的经验是，"
                + fact + " 建议每月第" + day + "天做一次简单检查，并记录结果方便以后搜索。";
    }
}
