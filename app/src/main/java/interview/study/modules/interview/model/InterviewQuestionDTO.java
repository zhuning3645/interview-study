package interview.study.modules.interview.model;

/**
 * 面试问题
 */
public record InterviewQuestionDTO (
        int questionIndex,
        String question,
        QuestionType type,
        String category, // 问题类别：项目经历、Java基础、集合、并发、MySQL、Redis、Spring、SpringBoot
        String userAnswer, // 用户回答
        Integer score, // 单题得分 (0-100)
        String feedback, // 单题反馈
        boolean isFollowUp, // 是否为追问
        Integer parentQuestionIndex // 追问关联的主问题索引
){
    public enum QuestionType{
        PROJECT,          // 项目经历
        JAVA_BASIC,       // Java基础
        JAVA_COLLECTION,  // Java集合
        JAVA_CONCURRENT,  // Java并发
        MYSQL,            // MySQL
        REDIS,            // Redis
        SPRING,           // Spring
        SPRING_BOOT       // Spring Boot
    }

    /**
     * 创建新问题（未回答状态
     * @param index
     * @param question
     * @param type
     * @param category
     * @return
     */
    public static InterviewQuestionDTO create(
            int index,
            String question,
            QuestionType type,
            String category){
        return new InterviewQuestionDTO(index, question, type, category, null, null, null, false, null);
    }

    /**
     * 创建新问题（支持追问标记
     * @param index
     * @param question
     * @param type
     * @param category
     * @param isFollowUp
     * @param parentQuestionIndex
     * @return
     */
    public static InterviewQuestionDTO create(
            int index,
            String question,
            QuestionType type,
            String category,
            boolean isFollowUp,
            Integer parentQuestionIndex
    ){
        return new InterviewQuestionDTO(index, question, type, category, null, null, null, isFollowUp, parentQuestionIndex);
    }

    /**
     * 添加用户回答
     * @param answer
     * @return
     */
    public InterviewQuestionDTO withAnswer(String answer){
        return new InterviewQuestionDTO(
                questionIndex, question, type, category, answer, score, feedback, isFollowUp, parentQuestionIndex
        );
    }

    /**
     * 添加评分和反馈
     * @param score
     * @param feedback
     * @return
     */
    public InterviewQuestionDTO withEvaluation(int score, String feedback){
        return new InterviewQuestionDTO(
                questionIndex, question, type, category, userAnswer, score, feedback, isFollowUp, parentQuestionIndex
        );
    }


}
