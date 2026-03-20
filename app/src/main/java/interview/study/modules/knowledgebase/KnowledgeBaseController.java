package interview.guide.modules.knowledgebase;

import interview.guide.common.result.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/knowledgebase")
public class KnowledgeBaseController {

    @GetMapping("/health")
    public ApiResponse<String> health() {
        return ApiResponse.ok("knowledgebase module ready");
    }
}
