package interview.guide.modules.resume;

import interview.guide.common.result.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/resume")
public class ResumeController {

    @GetMapping("/health")
    public ApiResponse<String> health() {
        return ApiResponse.ok("resume module ready");
    }
}
