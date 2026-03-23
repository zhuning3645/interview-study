package interview.study.modules.interview.service;



import interview.study.common.exception.BusinessException;
import interview.study.common.exception.ErrorCode;
import interview.study.modules.interview.model.InterviewQuestionDTO;
import interview.study.modules.interview.model.InterviewReportDTO;
import jakarta.annotation.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.chat.prompt.SystemPromptTemplate;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;


import java.io.IOException;
import java.nio.charset.StandardCharsets;
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

    public AnswerEvaluationService(
            ChatClient.Builder chatClientBuilder,
            StructuredOutputInvoker structuredOutputInvoker,
            @Value("classpath:prompts/interview-evaluation-system.st") Resource systemPromptResource,
            @Value("classpath:prompts/interview-evaluation-user.st") Resource userPromptResource,
            @Value("classpath:prompts/interview-evaluation-summary-system.st")Resource summarySystemPromptResource,
            @Value("classpath:prompts/interview-evaluation-summary-user.st")Resource summaryUserPromptResource,
            @Value("${APP_INTERVIEW_EVALUATION_BATCH_SIZE:8}")int evaluationBatchSize) throws IOException {
        this.chatClient = chatClientBuilder.build();
        this.structuredOutputInvoker = structuredOutputInvoker;
        this.systemPromptTemplate = new PromptTemplate(systemPromptResource.getContentAsString(StandardCharsets.UTF_8));
        this.userPromptTemplate = new PromptTemplate(userPromptResource.getContentAsString(StandardCharsets.UTF_8));
        this.outputConverter = new BeanOutputConverter<>(EvaluationReportDTO.class);
        this.summarySystemPromptTemplate = new PromptTemplate(summarySystemPromptResource.getContentAsString(StandardCharsets.UTF_8));
        this.summaryUserPromptTemplate = new PromptTemplate(summaryUserPromptResource.getContentAsString(StandardCharsets.UTF_8));
        this.summaryOutputConverter = new BeanOutputConverter<>(FinalSummaryDTO.class);
        this.evaluationBatchSize = Math.max(1, evaluationBatchSize);
    }


    /**
     * 评估完整面试并生成报告
     * @return
     */
    public InterviewReportDTO evaluateInterview(String sessionId, String resumeText,
                                                List<InterviewQuestionDTO> questions){
        log.info("开始评估面试:{},共{}题", sessionId, questions.size());

        try{
            //简历摘要（限制长度
            String resumeSummary = resumeText.length() > 500
                    ? resumeText.substring(0, 500) + "..."
                    : resumeText;
            //分配评估

            //转换为业务对象
            return converToReport{
                sessionId,
                mergeEvaluations,
                questions,
                finalSummary.overallFeedback(),
                finalSummary.strengths(),
                finalSummary.improvements()
            };
        }catch (interview.guide.common.exception.BusinessException e){
            //重新抛出业务异常
            throw e;
        }catch (Exception e){
            log.error("面试评估失败：{}", e.getMessage(),e);
            throw new BusinessException(ErrorCode.INTERVIEW_EVALUATION_FAILED,
                    "面试评估失败：" + e.getMessage());
        }
    }

    /**
     * 构建问答记录字符串
     * @param questions
     * @return
     */
    public String buildQARecords(List<InterviewQuestionDTO>questions){

    }
}
