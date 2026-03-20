package interview.study.modules.interview;

import interview.guide.common.result.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import lombok.extern.slf4j.Slf4j;

/**
 * 面试control层
 * 负责面试相关的业务逻辑接口
 */
@Slf4j
@RestController
@RequestMapping("/api/interview")
public class InterviewController {

    @GetMapping("/health")
    public ApiResponse<String> health() {
        return ApiResponse.ok("interview module ready");
    }
}
