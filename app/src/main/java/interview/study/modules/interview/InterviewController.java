package interview.guide.modules.interview;

import interview.guide.common.result.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/interview")
public class InterviewController {

    @GetMapping("/health")
    public ApiResponse<String> health() {
        return ApiResponse.ok("interview module ready");
    }
}
