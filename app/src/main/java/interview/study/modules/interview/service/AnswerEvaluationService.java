package interview.study.modules.interview.service;



import interview.study.modules.interview.model.InterviewQuestionDTO;
import interview.study.modules.interview.model.InterviewReportDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.chat.prompt.SystemPromptTemplate;
import org.springframework.stereotype.Service;


import java.util.List;

@Service
public class AnswerEvaluationService {

    private static final Logger log = LoggerFactory.getLogger(AnswerEvaluationService.class);

    private final ChatClient chatClient;
    private final ChatClient.Builder chatClientBuilder;
    private fianl PromptTemplate SystemPromptTemplate;
    private fianl PromptTemplate userPromptTemplate;
    private fianl BeanOutputConverter<EvaluationReportDTO>outputConverter;
    private final PromptTemplate summarySystemPromptTemplate;
    private final PromptTemplate summaryUserPromptTemplate;
    private fianl BeanOutputConverter<FinalSummaryDTO>summaryOutputConverter;
    private final StructuredOUtputInvoker structuredOUtputInvoker;
    private final int evaluationBatchSize;

    //中间DTO用于接收AI响应
    private record EvaluationReportDTO(
            int overallScore,
            String overallFeedback,
            List<String>strengths,
            List<String>improvements,
            List<QuestionEvaluationDTO>questionEvaluations;
    ){}

    public AnswerEvaluationService(ChatClient chatClient, ChatClient.Builder chatClientBuilder) {
        this.chatClient = chatClientBuilder.build();
        this.chatClientBuilder = chatClientBuilder;
    }


    /**
     * 评估完整面试并生成报告
     * @return
     */
    public InterviewReportDTO evaluateInterview(){

    }

    /**
     * 构建问答记录字符串
     * @param questions
     * @return
     */
    public String buildQARecords(List<InterviewQuestionDTO>questions){

    }
}
