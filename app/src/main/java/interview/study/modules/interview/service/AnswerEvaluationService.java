package interview.study.modules.interview.service;


import interview.study.common.ai.StructuredOutputInvoker;
import interview.study.common.exception.BusinessException;
import interview.study.common.exception.ErrorCode;
import interview.study.modules.interview.model.InterviewQuestionDTO;
import interview.study.modules.interview.model.InterviewReportDTO;
import jakarta.annotation.Resource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;


import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 面试回答评估服务
 * 负责调用AI模型对用户的面试回答进行评估，生成评估报告，包括得分、优缺点及建议等。
 * 支持分批评估以应对长篇幅的上下文，并最终汇总生成完整的面试报告。
 */
@Service
public class AnswerEvaluationService {

    private static final Logger log = LoggerFactory.getLogger(AnswerEvaluationService.class);

    private final ChatClient chatClient;
    private final PromptTemplate systemPromptTemplate;
    private final PromptTemplate userPromptTemplate;
    private final BeanOutputConverter<EvaluationReportDTO> outputConverter;
    private final PromptTemplate summarySystemPromptTemplate;
    private final PromptTemplate summaryUserPromptTemplate;
    private final BeanOutputConverter<FinalSummaryDTO> summaryOutputConverter;
    private final StructuredOutputInvoker structuredOutputInvoker;
    private final int evaluationBatchSize;

    //中间DTO用于接收AI响应
    private record EvaluationReportDTO(
            int overallScore,
            String overallFeedback,
            List<String> strengths,
            List<String> improvements,
            List<QuestionEvaluationDTO> questionEvaluations
    ) {
    }

    private record QuestionEvaluationDTO(
            int questionIndex,
            int score,
            String feedback,
            String referenceAnswer,
            List<String> keyPoints
    ) {
    }

    private record BatchEvaluationResult(
            int startIndex,
            int endIndex,
            EvaluationReportDTO report
    ) {
    }

    private record FinalSummaryDTO(
            String overallFeedback,
            List<String> strengths,
            List<String> improvements
    ) {
    }

    public AnswerEvaluationService(
            ChatClient.Builder chatClientBuilder,
            StructuredOutputInvoker structuredOutputInvoker,
            @Value("classpath:prompts/interview-evaluation-system.st") Resource systemPromptResource,
            @Value("classpath:prompts/interview-evaluation-user.st") Resource userPromptResource,
            @Value("classpath:prompts/interview-evaluation-summary-system.st") Resource summarySystemPromptResource,
            @Value("classpath:prompts/interview-evaluation-summary-user.st") Resource summaryUserPromptResource,
            @Value("${APP_INTERVIEW_EVALUATION_BATCH_SIZE:8}") int evaluationBatchSize) throws IOException {
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
     * 评估完整面试并生成报告。
     * 采用分批处理以避免单次请求 token 过长的问题，并将分批结果最终合并总结为完整的评估报告。
     *
     * @param sessionId  面试会话 ID
     * @param resumeText 候选人简历文本（自动截取前500字符以减小 token 消耗）
     * @param questions  面试题目及用户的回答列表
     * @return 完整的面试评估报告DTO
     * @throws BusinessException 评估过程中若发生异常则抛出业务异常
     */
    public InterviewReportDTO evaluateInterview(String sessionId, String resumeText,
                                                List<InterviewQuestionDTO> questions) {
        log.info("开始评估面试:{},共{}题", sessionId, questions.size());

        try {
            //简历摘要（限制长度
            String resumeSummary = resumeText.length() > 500
                    ? resumeText.substring(0, 500) + "..."
                    : resumeText;
            //分批评估，防止单次上下文过大导致token超限
            List<BatchEvaluationResult> batchResults = evaluateInBatches(sessionId, resumeSummary, questions);

            List<QuestionEvaluationDTO> mergeEvaluations = mergeQuestionEvaluations(batchResults);
            String fallbackOverallFeedback = mergeOverallFeedback(batchResults);
            List<String> fallbackStrengths = mergeListItems(batchResults, true);
            List<String> fallbackImprovements = mergeListItems(batchResults, false);
            FinalSummaryDTO finalSummary = summarizeBatchResults(
                    sessionId,
                    resumeSummary,
                    questions,
                    mergeEvaluations,
                    fallbackOverallFeedback,
                    fallbackStrengths,
                    fallbackImprovements
            );

            //转换为业务对象
            return convertToReport(
                    sessionId,
                    mergeEvaluations,
                    questions,
                    finalSummary.overallFeedback(),
                    finalSummary.strengths(),
                    finalSummary.improvements()
            );

        } catch (BusinessException e) {
            //重新抛出业务异常
            throw e;
        } catch (Exception e) {
            log.error("面试评估失败：{}", e.getMessage(), e);
            throw new BusinessException(ErrorCode.INTERVIEW_EVALUATION_FAILED,
                    "面试评估失败：" + e.getMessage());
        }
    }

    /**
     * 将面试题目与用户的回答拼接为一段供AI阅读的问答记录文本。
     *
     * @param questions 待拼接的面试问题列表
     * @return 格式化后的问答记录字符串，如："问题1 [Java]: 什么是JVM?\n回答：不知道\n\n"
     */
    public String buildQARecords(List<InterviewQuestionDTO> questions) {
        StringBuilder sb = new StringBuilder();
        for (InterviewQuestionDTO question : questions) {
            sb.append(String.format("问题%d [%s]: %s\n",
                    question.questionIndex() + 1, question.category(), question.question()));
            sb.append(String.format("回答：%s\n\n",
                    question.userAnswer() != null ? question.userAnswer() : "(未回答)"));
        }
        return sb.toString();
    }

    /**
     * 将整个面试的问题列表按预设大小进行分批评估，以防止单次 AI 请求由于输入过长而超限。
     *
     * @param sessionId     面试会话 ID
     * @param resumeSummary 缩略版的候选人简历文本
     * @param questions     待评估的完整问题列表
     * @return 分批评估结果的列表，每个元素包含批次的起始索引及对应的 AI 评估报告
     */
    private List<BatchEvaluationResult> evaluateInBatches(
            String sessionId,
            String resumeSummary,
            List<InterviewQuestionDTO> questions
    ) {
        List<BatchEvaluationResult> results = new ArrayList<>();
        for (int start = 0; start < questions.size(); start += evaluationBatchSize) {
            int end = Math.min(start + evaluationBatchSize, questions.size());
            List<InterviewQuestionDTO> batchQuestions = questions.subList(start, end);
            EvaluationReportDTO report = evaluateBatch(sessionId, resumeSummary, batchQuestions, start, end);
            results.add(new BatchEvaluationResult(start, end, report));
        }
        return results;
    }

    /**
     * 对特定批次的问题及用户的回答调用 AI 模型进行单次评估。
     *
     * @param sessionId      面试会话 ID
     * @param resumeSummary  缩略版的候选人简历
     * @param batchQuestions 当前批次要评估的问题列表
     * @param start          本批次在完整列表中的起始索引（包含）
     * @param end            本批次在完整列表中的结束索引（不包含）
     * @return 当前批次的 AI 评估报告结果，若评估失败抛出业务异常
     */
    private EvaluationReportDTO evaluateBatch(
            String sessionId,
            String resumeSummary,
            List<InterviewQuestionDTO> batchQuestions,
            int start,
            int end
    ) {
        String qaRecords = buildQARecords(batchQuestions);
        String systemPrompt = systemPromptTemplate.render();

        Map<String, Object> variables = new HashMap<>();
        variables.put("resumeText", resumeSummary);
        variables.put("qaRecords", qaRecords);
        String userPrompt = userPromptTemplate.render(variables);

        String systemPromptWithFormat = systemPrompt + "\n\n" + outputConverter.getFormat();
        try {
            EvaluationReportDTO dto = structuredOutputInvoker.invoke(
                    chatClient,
                    systemPromptWithFormat,
                    userPrompt,
                    outputConverter,
                    ErrorCode.INTERVIEW_EVALUATION_FAILED,
                    "面试评估失败：",
                    "批次评估",
                    log
            );
            log.debug("批次评估完成：sessionId={}, range =[{}, {}], batchSize = {} ",
                    sessionId, start, end, batchQuestions.size());
            return dto;
        } catch (Exception e) {
            log.error("批次评估失败：sessionId = {},range =[{}, {}], batchSize = {}",
                    sessionId, start, end, e.getMessage(), e);
            throw new BusinessException(ErrorCode.INTERVIEW_EVALUATION_FAILED, "面试评估失败:" + e.getMessage());
        }
    }

    /**
     * 合并分批获取到的各题目评估结果。
     * 若某批次的 AI 返回结果丢失，自动填充该批次各问题的默认低分报告。
     *
     * @param batchResults 分批评估的结果列表
     * @return 完整的、顺序排列的单个问题评估结果集合
     */
    private List<QuestionEvaluationDTO> mergeQuestionEvaluations(List<BatchEvaluationResult> batchResults) {
        List<QuestionEvaluationDTO> merged = new ArrayList<>();
        for (BatchEvaluationResult result : batchResults) {
            int expectedSize = result.endIndex() - result.startIndex();
            List<QuestionEvaluationDTO> current =
                    result.report() != null && result.report().questionEvaluations() != null
                            ? result.report().questionEvaluations()
                            : List.of();
            for (int i = 0; i < expectedSize; i++) {
                if (i < current.size() && current.get(i) != null) {
                    merged.add(current.get(i));
                } else {
                    merged.add(new QuestionEvaluationDTO(
                            result.startIndex() + i,
                            0,
                            "该题未成功生成评估结果，系统按0分处理",
                            "",
                            List.of()
                    ));
                }
            }
        }
        return merged;
    }

    /**
     * 合并所有批次评估的整体反馈意见，采用双换行符分隔。
     * 若均无有效反馈，则提供默认的保底文案。
     *
     * @param batchResults 分批评估的结果列表
     * @return 最终聚合的整体面试反馈文本
     */
    private String mergeOverallFeedback(List<BatchEvaluationResult> batchResults) {
        String feedback = batchResults.stream()
                .map(BatchEvaluationResult::report)
                .filter(r -> r != null && r.overallFeedback() != null && !r.overallFeedback().isBlank())
                .map(EvaluationReportDTO::overallFeedback)
                .collect(Collectors.joining("\n\n"));
        if (!feedback.isBlank()) {
            return feedback;
        }
        return "本次面试已完成分批评估，但未生成有效综合评语";
    }

    /**
     * 合并分批结果中的优点或改进建议，去重并限制返回条数以避免内容冗余。
     *
     * @param batchResults  分批评估结果列表
     * @param strengthsMode 如果为 true，则合并优点 (strengths)；否则合并改进建议 (improvements)
     * @return 合并去重后、最多 8 条的列表项
     */
    private List<String> mergeListItems(List<BatchEvaluationResult> batchResults, boolean strengthsMode) {
        Set<String> merged = new LinkedHashSet<>();
        for (BatchEvaluationResult result : batchResults) {
            EvaluationReportDTO report = result.report();
            if (report == null) {
                continue;
            }
            List<String> items = strengthsMode ? report.strengths() : report.improvements();
            if (items == null) {
                continue;
            }
            items.stream()
                    .filter(item -> item != null && !item.isBlank())
                    .map(String::trim)
                    .forEach(merged::add);
        }
        return merged.stream().limit(8).toList();
    }

    /**
     * 调用 AI 对前置的多个批次的结果进行最终全局摘要，提取统一的反馈、优势和不足。
     * 若该步骤失败，则退化使用之前各批次简单拼接的保底结果。
     *
     * @param sessionId               面试会话 ID
     * @param resumeSummary           缩略版候选人简历
     * @param questions               面试题列表
     * @param evaluations             所有合并后的单题评估数据
     * @param fallbackOverallFeedback 保底的综合评语文本（拼接生成）
     * @param fallbackStrengths       保底的优点列表（合并去重）
     * @param fallbackImprovements    保底的改进建议列表（合并去重）
     * @return 全局汇总后的综合评价数据，若出错则返回空DTO或带默认值的DTO
     */
    private FinalSummaryDTO summarizeBatchResults(
            String sessionId,
            String resumeSummary,
            List<InterviewQuestionDTO> questions,
            List<QuestionEvaluationDTO> evaluations,
            String fallbackOverallFeedback,
            List<String> fallbackStrengths,
            List<String> fallbackImprovements
    ) {
        try {
            String summarySystemPrompt = summarySystemPromptTemplate.render();
            Map<String, Object> variables = new HashMap<>();
            variables.put("resumeText", resumeSummary);
            variables.put("categorySummary", buildCategorySummary(questions, evaluations));
            variables.put("questionHighlight", buildQuestionHighlights(questions, evaluations));
            variables.put("fallbackOverallFeedback", fallbackOverallFeedback);
            variables.put("fallbackStrengths", String.join("\n", fallbackStrengths));
            variables.put("fallbackImprovements", String.join("\n", fallbackImprovements));
            String summaryUserPrompt = summaryUserPromptTemplate.render(variables);

            String systemPromptWithFormat = summarySystemPrompt + "\n\n" + summaryOutputConverter.getFormat();
            FinalSummaryDTO dto = structuredOutputInvoker.invoke(
                    chatClient,
                    systemPromptWithFormat,
                    summaryUserPrompt,
                    summaryOutputConverter,
                    ErrorCode.INTERVIEW_EVALUATION_FAILED,
                    "面试总结失败",
                    "总结评估",
                    log
            );
            String overallFeedback = dto != null && dto.overallFeedback() != null && !dto.overallFeedback().isBlank()
                    ? dto.overallFeedback()
                    : fallbackOverallFeedback;
            List<String> strengths = sanitizeSummaryItems(
                    dto != null ? dto.strengths() : null,
                    fallbackStrengths
            );
            List<String> improvements = sanitizeSummaryItems(
                    dto != null ? dto.improvements() : null,
                    fallbackImprovements
            );

            log.debug("二次汇总评估完成: sessionId = {}", sessionId);
            return new FinalSummaryDTO(overallFeedback, strengths, improvements);

        } catch (Exception e) {
            log.warn("二次汇总评估失败，降级到批次聚合结果：sessionId = {}，error={}", sessionId, e.getMessage());
            return new FinalSummaryDTO(
                    fallbackOverallFeedback,
                    fallbackStrengths,
                    fallbackImprovements
            );
        }
    }

    /**
     * 清理 AI 总结结果，过滤空字符串并限制最大条目数。
     * 若主要列表无效，则启用降级列表作为备选数据。
     *
     * @param primary  AI返回的列表数据
     * @param fallback 当 primary 无效或为空时使用的备选数据
     * @return 经过过滤去重且受长度限制的列表集合
     */
    private List<String> sanitizeSummaryItems(List<String> primary, List<String> fallback) {
        List<String> source = (primary != null && !primary.isEmpty()) ? primary : fallback;
        if (source == null || source.isEmpty()) {
            return List.of();
        }
        return source.stream()
                .filter(item -> item != null && !item.isBlank())
                .map(String::trim)
                .distinct()
                .limit(8)
                .toList();
    }

    /**
     * 构建各知识维度分类（Category）的数据统计摘要文本。
     * 仅当用户作答时才统计分数，并分别计算均分和问题数量。
     *
     * @param questions   整个面试题库
     * @param evaluations 各题评分和反馈数据集合
     * @return 分类维度成绩统计字符串（多行）
     */
    private String buildCategorySummary(List<InterviewQuestionDTO> questions, List<QuestionEvaluationDTO> evaluations) {
        Map<String, List<Integer>> categoryScores = new HashMap<>();
        for (int i = 0; i < questions.size(); i++) {
            InterviewQuestionDTO q = questions.get(i);
            QuestionEvaluationDTO eval = i < evaluations.size() ? evaluations.get(i) : null;
            int score = 0;
            if (eval != null && q.userAnswer() != null && !q.userAnswer().isBlank()) {
                score = eval.score();
            }
            categoryScores.computeIfAbsent(q.category(), k -> new ArrayList<>()).add(score);
        }

        return categoryScores.entrySet().stream()
                .map(entry -> {
                    int count = entry.getValue().size();
                    int avg = (int) entry.getValue().stream().mapToInt(Integer::intValue).average().orElse(0);
                    return String.format("- %s:平均分 %d, 题数 %d", entry.getKey(), avg, count);
                })
                .sorted()
                .collect(Collectors.joining("\n"));
    }


    /**
     * 构建核心答题亮点的反馈摘要。
     * 截断问题内容和反馈，以简洁的单行形式展示各题的分数和简评。
     *
     * @param questions   整个面试题库
     * @param evaluations 对各个问题的评估反馈
     * @return 最多 20 条题目的单行评分反馈集合
     */
    private String buildQuestionHighlights(List<InterviewQuestionDTO> questions, List<QuestionEvaluationDTO> evaluations) {
        List<String> highlights = new ArrayList<>();
        for (int i = 0; i < questions.size(); i++) {
            InterviewQuestionDTO q = questions.get(i);
            QuestionEvaluationDTO eval = i < evaluations.size() ? evaluations.get(i) : null;
            int score = eval != null ? eval.score() : 0;
            String feedback = eval != null && eval.feedback() != null ? eval.feedback() : "";
            String questionText = q.question() != null ? q.question() : "";
            String shortQuestion = questionText.length() > 50 ? questionText.substring(0, 50) + "..." : questionText;
            String shortFeedback = feedback.length() > 80 ? feedback.substring(0, 80) + "..." : feedback;
            highlights.add(String.format("- Q%d | %s | 分数:%d | 反馈:%s",
                    q.questionIndex() + 1, shortQuestion, score, shortFeedback));
        }
        return highlights.stream().limit(20).collect(Collectors.joining("\n"));
    }

    /**
     * 将评估中间模型 DTO 转换并整合为最终面向业务侧的完整面试报告。
     * 包括汇总各项指标、重算总分以及处理各项防御性校验。
     *
     * @param sessionId       面试会话 ID
     * @param evaluations     完整合并的问题评估详情列表
     * @param questions       用户的面试题目与作答列表
     * @param overallFeedback 面试全局反馈总结
     * @param strengths       受肯定的主要优势列表
     * @param improvements    建议的主要改进点列表
     * @return 业务层的完整面试报告 InterviewReportDTO
     */
    private InterviewReportDTO convertToReport(
            String sessionId,
            List<QuestionEvaluationDTO> evaluations,
            List<InterviewQuestionDTO> questions,
            String overallFeedback,
            List<String> strengths,
            List<String> improvements
    ) {
        List<QuestionEvaluationDTO> questionDetails = new ArrayList<>();
        List<ReferenceAnswer> referenceAnswers = new ArrayList<>();
        Map<String, List<Integer>> categoryScoresMap = new HashMap<>();

        // 统计实际回答的问题数量
        long answeredCount = questions.stream()
                .filter(q -> q.userAnswer() != null && !q.userAnswer().isBlank())
                .count();
        //处理问题评估（防御性编程：AI响应解析后可能为null
        int evaluationsSize = evaluations != null ? evaluations.size() : 0;
        if (evaluations == null || evaluations.isEmpty()) {
            log.warn("面试评估结果解析异常：问题评估列表为空，sessionId = {}", sessionId);
        }
        for (int i = 0; i < questions.size(); i++) {
            QuestionEvaluationDTO eval = i < evaluationsSize ? evaluations.get(i) : null;
            InterviewQuestionDTO q = questions.get(i);
            int qIndex = q.questionIndex();
            String feedback = eval != null && eval.feedback() != null
                    ? eval.feedback()
                    : "该题未成功生成评估反馈。";
            String referenceAnswer = eval != null && eval.referenceAnswer() != null
                    ? eval.referenceAnswer()
                    : "";
            List<String> keyPoints = eval != null && eval.keyPoints() != null
                    ? eval.keyPoints()
                    : List.of();

            //如果用户未回答该题，分数强制为0
            boolean hasAnswer = q.userAnswer() != null && !q.userAnswer().isBlank();
            int score = hasAnswer && eval != null ? eval.score() : 0;

            questionDetails.add(new QuestionEvaluation(
                    qIndex, q.question(), q.category(),
                    q.userAnswer(), score, feedback
            ));

            referenceAnswers.add(new ReferenceAnswer(
                    qIndex, q.question(),
                    referenceAnswers,
                    keyPoints
            ));

            //收集类别分数
            categoryScoresMap
                    .computeIfAbsent(q.category(), k -> new ArrayList<>())
                    .add(score);
        }

        //计算各类别平均分
        List<CategoryScore> categoryScores = categoryScoresMap.entrySet().stream()
                .map(e -> new CategoryScore(
                        e.getKey(),
                        (int) e.getValue().stream().mapToInt(Integer::intValue).average().orElse(0),
                        e.getValue().size()
                ))
                .collect(Collectors.toList());

        //计算总分：基于实际得分，而非AI的返回值
        // 如果所有问题都未回答，总分为0
        int overallScore;
        if (answeredCount == 0) {
            overallScore = 0;
        } else {
            //使用问题详情中的分数计算平均值
            overallScore = (int) questionDetails.stream()
                    .mapToInt(QuestionEvaluationDTO::score)
                    .average()
                    .orElse(0);
        }

        return new InterviewReportDTO(
                sessionId,
                questions.size(),
                overallScore,
                categoryScores,
                overallFeedback,
                strengths != null ? strengths : List.of(),
                improvements != null ? improvements : List.of(),
                referenceAnswers
        );
    }
}
