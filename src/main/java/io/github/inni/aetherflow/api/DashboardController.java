package io.github.inni.aetherflow.api;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
@RequestMapping("/dashboard")
@ConditionalOnProperty(name = "aetherflow.api.enabled", havingValue = "true", matchIfMissing = true)
public class DashboardController {

	@GetMapping
	public String index() {
		return "dashboard/index";
	}

	@GetMapping("/runs")
	public String runs(
		@RequestParam(required = false) String workflowName,
		@RequestParam(required = false) String status,
		Model model
	) {
		model.addAttribute("filterWorkflow", workflowName != null ? workflowName : "");
		model.addAttribute("filterStatus", status != null ? status : "");
		return "dashboard/runs";
	}

	@GetMapping("/runs/{id}")
	public String runDetail(@PathVariable String id, Model model) {
		model.addAttribute("runId", id);
		return "dashboard/run-detail";
	}
}
