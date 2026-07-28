const byId = id => document.getElementById(id);
let state = null;
let activeConfiguration = null;
let activeTrace = null;
let configurationExamples = {};
let bundledBatch = null;
let configurationEditorDirty = false;
let batchJobId = null;
let batchResults = [];
let comparisonSort = { key: "scenarioName", direction: 1 };

async function request(path, values = null) {
    const options = values === null
        ? {}
        : {
            method: "POST",
            headers: { "Content-Type": "application/x-www-form-urlencoded" },
            body: new URLSearchParams(values)
        };
    const response = await fetch(path, options);
    const payload = await response.json();
    if (!response.ok) throw new Error(payload.error || `Request failed (${response.status})`);
    return payload;
}

async function requestJson(path, value) {
    const response = await fetch(path, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(value)
    });
    const payload = await response.json();
    if (!response.ok) throw new Error(payload.error || `Request failed (${response.status})`);
    return payload;
}

async function refresh() {
    [state, activeConfiguration] = await Promise.all([
        request("/api/state"),
        request("/api/configuration")
    ]);
    render();
}

async function loadCatalogues() {
    [configurationExamples, bundledBatch] = await Promise.all([
        request("/api/configurations/examples"),
        request("/api/batch/example")
    ]);
    renderConfigurationChoices();
    renderBatchConfigurationChoices();
}

function render() {
    byId("knowledge-version").textContent = `K${state.knowledgeVersion}`;
    byId("current-mode").textContent = state.currentMode;
    activeTrace = state.traces.length ? state.traces[state.traces.length - 1] : null;
    renderConfiguration();
    renderLlmConnector();
    renderLadder();
    populateEvidence();
    renderGovernance();
    renderCandidates();
    renderExecution();
    renderTraces();
}

function renderConfiguration() {
    if (!activeConfiguration) return;
    byId("configuration-badge").textContent = activeConfiguration.name;
    byId("configuration-thresholds").textContent =
        `${activeConfiguration.thresholds.tauA} / ${activeConfiguration.thresholds.tauAS} / ${activeConfiguration.thresholds.tauD} / ${activeConfiguration.thresholds.tauR}; qmin ${activeConfiguration.thresholds.qMin}`;
    byId("configuration-weights").textContent =
        `sustainability ${activeConfiguration.weights.sustainability}; autonomy ${activeConfiguration.weights.autonomy}`;
    byId("configuration-plans").textContent = `${activeConfiguration.plans.length} validated definitions`;
    byId("configuration-schema").textContent = activeConfiguration.schemaVersion;
    byId("download-configuration").setAttribute(
        "download",
        `${activeConfiguration.id}.json`
    );
    if (!configurationEditorDirty) {
        byId("configuration-editor").value = JSON.stringify(activeConfiguration, null, 2);
    }
}

function renderConfigurationChoices() {
    const select = byId("configuration-example");
    for (const option of select.options) {
        if (!configurationExamples[option.value]) option.disabled = true;
    }
}

function renderBatchConfigurationChoices() {
    const labels = {
        worked: "Worked example · reference",
        "advisory-first": "Advisory-first plan calibration",
        conservative: "Conservative threshold policy"
    };
    byId("batch-configuration-list").innerHTML = Object.entries(configurationExamples)
        .map(([id, configuration]) => `
            <label class="check-card">
                <input type="checkbox" value="${escapeHtml(id)}" ${id === "worked" ? "checked" : ""}>
                <span><strong>${escapeHtml(labels[id] || configuration.name)}</strong><small>${escapeHtml(configuration.id)}</small></span>
            </label>
        `).join("");
}

function renderLlmConnector() {
    const config = state.configuration.llm;
    const form = byId("llm-form");
    form.elements.enabled.checked = config.enabled;
    form.elements.evidenceDisclosureConsent.checked = config.evidenceDisclosureConsent;
    form.elements.endpoint.value = config.endpoint;
    form.elements.protocol.value = config.protocol;
    form.elements.model.value = config.model;
    form.elements.authHeader.value = config.authHeader;
    form.elements.authScheme.value = config.authScheme;
    form.elements.timeoutSeconds.value = config.timeoutSeconds;
    form.elements.apiKey.value = "";

    const badge = byId("llm-state-badge");
    badge.textContent = !config.enabled
        ? "Disabled · local baseline"
        : config.evidenceDisclosureConsent ? "Enabled · governed" : "Enabled · consent required";
    badge.className = `connector-badge${config.enabled ? " active" : ""}`;
    byId("llm-endpoint").textContent = config.endpoint;
    byId("llm-model").textContent = `${config.model} · ${human(config.protocol)}`;
    byId("llm-key-status").textContent = config.apiKeyConfigured
        ? "Configured in process memory"
        : "Not configured";
    byId("llm-consent-status").textContent =
        config.evidenceDisclosureConsent ? "Granted" : "Not granted";

    const update = state.latestCognitiveUpdate;
    byId("llm-update-status").textContent =
        update ? human(update.status) : "No cycle yet";
    byId("llm-latency").textContent =
        update && update.latencyMillis ? `${update.latencyMillis} ms` : "-";
    byId("llm-summary").textContent =
        update ? update.summary : "The deterministic EASE baseline is active.";
}

function renderLadder() {
    byId("autonomy-ladder").innerHTML = state.configuration.autonomyLadder.map(mode => `
        <article class="mode-card ${mode.name === state.currentMode ? "active" : ""}">
            <span class="mode-rank">ρ = ${mode.rank}</span>
            <h3>${human(mode.name)}</h3>
            <p>${escapeHtml(mode.allocation)}</p>
        </article>
    `).join("");
}

function populateEvidence() {
    const form = byId("evidence-form");
    const evidence = state.currentEvidence;
    for (const key of [
        "purchasedPerishables",
        "discardedPerishables",
        "advisorySuggestions",
        "ignoredSuggestions",
        "sustainabilityConfidence"
    ]) {
        form.elements[key].value = evidence[key];
    }
    for (const key of [
        "externalDisclosureAttempt",
        "externalDisclosureConsent",
        "automaticListChangeConsent"
    ]) {
        form.elements[key].checked = evidence[key];
    }
    byId("confidence-output").textContent =
        Number(evidence.sustainabilityConfidence).toFixed(2);
}

function renderGovernance() {
    byId("empty-governance").hidden = Boolean(activeTrace);
    byId("governance-content").hidden = !activeTrace;
    if (!activeTrace) return;
    const governance = activeTrace.governance;
    byId("metric-m").textContent = number(governance.aggregateMismatch);
    byId("metric-q").textContent = number(governance.aggregateConfidence);
    byId("meter-m").style.width = `${governance.aggregateMismatch * 100}%`;
    byId("meter-q").style.width = `${governance.aggregateConfidence * 100}%`;
    byId("hard-state").textContent =
        governance.hardViolations.length ? governance.hardViolations.join(", ") : "∅";
    byId("threshold-region").textContent = human(governance.region);
    byId("policy-version").textContent =
        `${activeTrace.thresholds.version} · ${activeTrace.thresholds.calibrationStatus}`;
    byId("mismatch-list").innerHTML = activeTrace.mismatchIndicators.map(item => `
        <div class="mismatch-row ${item.hardViolation ? "hard-active" : ""}">
            <strong>${item.constraintId}</strong>
            <span>d ${number(item.deviation)}</span>
            <span>s ${number(item.severity)}</span>
            <span>m ${number(item.mismatch)}</span>
        </div>
    `).join("");
}

function renderCandidates() {
    byId("empty-candidates").hidden = Boolean(activeTrace);
    byId("candidate-content").hidden = !activeTrace;
    if (!activeTrace) return;
    byId("candidate-table").innerHTML = activeTrace.candidateIntentions.map(candidate => {
        const category = candidate.status.includes("SELECTED")
            ? "selected"
            : candidate.status.includes("REJECTED") ? "rejected" : "eligible";
        return `
            <tr class="${category === "selected" ? "selected" : ""}">
                <td><strong>${candidate.id}</strong><br>${escapeHtml(candidate.plan)}</td>
                <td>${human(candidate.mode)}<br><span class="mono">ρ=${candidate.intrusionRank}</span></td>
                <td class="mono">${number(candidate.predictedResidualMismatch)}<br><small>${human(candidate.predictionSource)}</small></td>
                <td><span class="pill ${category}">${human(candidate.status)}</span></td>
                <td>${escapeHtml(candidate.rationale)}</td>
            </tr>`;
    }).join("");
    byId("selection-rationale").textContent = activeTrace.leastIntrusiveRationale;
}

function renderExecution() {
    const card = byId("execution-card");
    const confirm = byId("confirm-intention");
    const contest = byId("contest-form");
    const result = byId("contestation-result");
    confirm.hidden = true;
    contest.hidden = !activeTrace;
    result.hidden = !activeTrace?.contestation;
    if (!activeTrace) {
        card.className = "empty-state";
        card.textContent = "No intention is awaiting action.";
        return;
    }
    const execution = state.executionRecords.find(record => record.traceId === activeTrace.id);
    const effectiveStatus = execution ? execution.status : activeTrace.executionStatus;
    card.className = "action-summary";
    const selected = activeTrace.candidateIntentions.find(
        candidate => candidate.id === activeTrace.selectedIntentionId
    );
    card.innerHTML = `
        <strong>${human(effectiveStatus)}</strong>
        <p>${selected
            ? `${selected.id} · ${escapeHtml(selected.plan)}`
            : "No admissible intention was committed."}</p>
    `;
    if (!execution && activeTrace.executionStatus === "PENDING_CONFIRMATION") {
        confirm.hidden = false;
        confirm.dataset.traceId = activeTrace.id;
    }
    contest.dataset.traceId = activeTrace.id;
    renderContestationForm(activeTrace);
    if (activeTrace.contestation) renderContestationResult(activeTrace);
}

function renderContestationForm(trace) {
    const evidence = trace.triggeringEvidence;
    byId("contest-originals").innerHTML = `
        <span>Recorded confidence <strong>${Number(evidence.sustainabilityConfidence).toFixed(2)}</strong></span>
        <span>External consent <strong>${yesNo(evidence.externalDisclosureConsent)}</strong></span>
        <span>Automatic consent <strong>${yesNo(evidence.automaticListChangeConsent)}</strong></span>
        <span>Discarded <strong>${evidence.discardedPerishables}</strong></span>
    `;
    const form = byId("contest-form");
    if (!form.elements.correctedConfidence.value) {
        form.elements.correctedConfidence.value = evidence.sustainabilityConfidence;
    }
    form.elements.correctedExternalDisclosureConsent.value =
        String(!evidence.externalDisclosureConsent);
    form.elements.correctedAutomaticListChangeConsent.value =
        String(!evidence.automaticListChangeConsent);
    const suggestedDiscarded = Math.max(0, evidence.discardedPerishables - 2);
    if (Number(form.elements.correctedDiscardedPerishables.value)
            === evidence.discardedPerishables) {
        form.elements.correctedDiscardedPerishables.value = suggestedDiscarded;
    }
}

function renderContestationResult(trace) {
    const contestation = trace.contestation;
    const changes = Object.keys(contestation.correctedValues).map(key => `
        <li><strong>${human(key)}</strong>
            <span>${escapeHtml(String(contestation.originalValues[key]))}</span>
            <b>→</b>
            <span>${escapeHtml(String(contestation.correctedValues[key]))}</span>
        </li>
    `).join("");
    byId("contestation-result").innerHTML = `
        <p class="eyebrow">Latest accepted contestation</p>
        <h3>${human(contestation.correctionType)}</h3>
        <ul>${changes}</ul>
        <p>${escapeHtml(contestation.reason)}</p>
        <small>Result: M ${number(trace.governance.aggregateMismatch)} · Q ${number(trace.governance.aggregateConfidence)} · ${escapeHtml(trace.selectedIntentionId || "no plan")}</small>
    `;
}

function renderTraces() {
    if (!state.traces.length) {
        byId("trace-list").innerHTML =
            '<div class="empty-state">No trace recorded yet.</div>';
        return;
    }
    const executionByTrace = Object.fromEntries(
        state.executionRecords.map(record => [record.traceId, record])
    );
    byId("trace-list").innerHTML = [...state.traces].reverse().map(trace => {
        const execution = executionByTrace[trace.id];
        const selected = trace.selectedIntentionId || "none";
        const related = trace.revisesTraceId
            ? `<span>revises ${shortId(trace.revisesTraceId)}</span>`
            : trace.contestation?.sourceTraceId
                ? `<span>related ${shortId(trace.contestation.sourceTraceId)}</span>`
                : "";
        return `
            <article class="trace-item">
                <div class="trace-top">
                    <strong>${shortId(trace.id)} · ${human(trace.phase)}</strong>
                    <time>${new Date(trace.createdAt).toLocaleTimeString()}</time>
                </div>
                <p>${escapeHtml(trace.leastIntrusiveRationale)}</p>
                <div class="trace-meta">
                    <span>K${trace.knowledgeVersionBefore} → K${trace.knowledgeVersionAfter}</span>
                    <span>M ${number(trace.governance.aggregateMismatch)}</span>
                    <span>${selected}</span>
                    <span>${execution ? human(execution.status) : human(trace.executionStatus)}</span>
                    <span>${human(trace.cognitiveUpdate?.status || "local baseline")}</span>
                    ${related}
                </div>
            </article>`;
    }).join("");
}

function selectedBatchDefinition() {
    const selected = [...byId("batch-configuration-list").querySelectorAll("input:checked")]
        .map(input => input.value);
    if (!selected.length) throw new Error("Select at least one configuration");
    const configurations = {};
    const scenarios = [];
    for (const id of selected) {
        configurations[id] = configurationExamples[id];
        scenarios.push({
            id: id === "worked" ? "worked-example" : id,
            name: configurationExamples[id].name,
            referenceScenario: id === "worked",
            configurationId: id
        });
    }
    if (byId("batch-include-correction").checked) {
        if (!configurations.worked) configurations.worked = configurationExamples.worked;
        scenarios.push({
            id: "historical-correction",
            name: "Worked example after historical correction",
            referenceScenario: false,
            configurationId: "worked",
            contestation: {
                correctionType: "HISTORICAL_FACT_CORRECTION",
                correctedDiscardedPerishables: 1,
                correctedConfidence: 0.96,
                reason: "Batch sensitivity correction of evidence and confidence."
            }
        });
    }
    return {
        schemaVersion: "ease-scenario-batch/v1",
        batchId: `ui-comparison-${Date.now()}`,
        name: "Dashboard configuration comparison",
        configurations,
        scenarios
    };
}

async function startBatch(definition, button = null) {
    if (!definition || !Array.isArray(definition.scenarios)) {
        return toast("A valid batch definition is required", true);
    }
    const buttons = [
        byId("run-selected-batch"),
        byId("load-example-batch")
    ];
    buttons.forEach(item => item.disabled = true);
    if (button) button.disabled = true;
    batchResults = [];
    batchJobId = null;
    renderComparison();
    setBatchProgress("STARTING", 0, definition.scenarios?.length || 0, 0);
    try {
        const job = await requestJson("/api/batch/start", definition);
        batchJobId = job.jobId;
        let status = job;
        while (["QUEUED", "RUNNING"].includes(status.state)) {
            setBatchProgress(status.state, status.completed, status.total, status.errors);
            batchResults = status.partialResults || [];
            renderComparison();
            await delay(250);
            status = await request(
                `/api/batch/status?jobId=${encodeURIComponent(batchJobId)}`
            );
        }
        setBatchProgress(status.state, status.completed, status.total, status.errors);
        if (status.state === "FAILED") {
            throw new Error(status.fatalError || "Batch job failed");
        }
        batchResults = status.result?.results || status.partialResults || [];
        renderComparison();
        enableBatchDownloads();
        toast(`Batch completed: ${status.completed - status.errors} succeeded, ${status.errors} errors`);
    } catch (error) {
        setBatchProgress("FAILED", 0, definition.scenarios?.length || 0, 1);
        toast(error.message, true);
    } finally {
        buttons.forEach(item => item.disabled = false);
        if (button) button.disabled = false;
    }
}

function setBatchProgress(status, completed, total, errors) {
    const percentage = total ? Math.round(completed * 100 / total) : 0;
    byId("batch-progress").value = percentage;
    byId("batch-progress-label").textContent =
        `${human(status)} · ${completed}/${total} scenarios`;
    byId("batch-error-count").textContent = `${errors} error${errors === 1 ? "" : "s"}`;
    const badge = byId("batch-state-badge");
    badge.textContent = human(status);
    badge.className = `connector-badge${status.startsWith("COMPLETED") ? " active" : ""}`;
}

function enableBatchDownloads() {
    for (const [id, format] of [
        ["download-batch-json", "json"],
        ["download-batch-csv", "csv"]
    ]) {
        const link = byId(id);
        link.href = `/api/batch/export?jobId=${encodeURIComponent(batchJobId)}&format=${format}`;
        link.classList.remove("disabled");
        link.removeAttribute("aria-disabled");
    }
}

function renderComparison() {
    const filter = byId("comparison-filter").value.trim().toLowerCase();
    let results = batchResults.filter(result =>
        !filter || JSON.stringify(result).toLowerCase().includes(filter)
    );
    results = [...results].sort((a, b) =>
        compareValues(sortValue(a, comparisonSort.key), sortValue(b, comparisonSort.key))
        * comparisonSort.direction
    );
    if (!results.length) {
        byId("comparison-table").innerHTML =
            '<tr><td colspan="9" class="empty-state">No matching scenario results.</td></tr>';
        byId("batch-errors").hidden = true;
        return;
    }
    byId("comparison-table").innerHTML = results.map(result => {
        const original = result.originalEvidence || {};
        const corrected = result.correctedEvidence;
        const finalMetrics = result.finalMetrics || {};
        const changed = result.changedParameters || {};
        const correctedConfidence = corrected?.sustainabilityConfidence;
        const correctedDiscarded = corrected?.discardedPerishables;
        return `
            <tr class="${result.referenceScenario ? "reference-row" : ""} ${result.status === "ERROR" ? "error-row" : ""}">
                <td><strong>${escapeHtml(result.scenarioName)}</strong>${result.referenceScenario ? '<span class="reference-label">Reference</span>' : ""}</td>
                <td>${escapeHtml(result.configurationName || result.configurationId)}</td>
                <td>${renderDifferences(changed, result.referenceScenario)}</td>
                <td class="${correctedConfidence !== undefined && correctedConfidence !== original.sustainabilityConfidence ? "modified-value" : ""}">
                    ${valueTransition(original.sustainabilityConfidence, correctedConfidence)}
                </td>
                <td class="${corrected ? "modified-value" : ""}">
                    <small>External</small> ${valueTransition(yesNo(original.externalDisclosureConsent), corrected ? yesNo(corrected.externalDisclosureConsent) : undefined)}
                    <br><small>Automatic</small> ${valueTransition(yesNo(original.automaticListChangeConsent), corrected ? yesNo(corrected.automaticListChangeConsent) : undefined)}
                </td>
                <td class="${correctedDiscarded !== undefined && correctedDiscarded !== original.discardedPerishables ? "modified-value" : ""}">
                    ${valueTransition(original.discardedPerishables, correctedDiscarded)}
                </td>
                <td class="mono">${metric(finalMetrics.aggregateMismatch)} / ${metric(finalMetrics.aggregateConfidence)}</td>
                <td><strong>${escapeHtml(result.finalPlanId || "—")}</strong><br><small>${human(result.finalMode || result.executionStatus)}</small></td>
                <td><span class="pill ${result.status === "SUCCESS" ? "selected" : "rejected"}">${human(result.status)}</span>${result.error ? `<p class="cell-error">${escapeHtml(result.error)}</p>` : ""}</td>
            </tr>
        `;
    }).join("");
    const errors = batchResults.filter(result => result.status === "ERROR");
    byId("batch-errors").hidden = !errors.length;
    byId("batch-errors").innerHTML = errors.map(result =>
        `<p><strong>${escapeHtml(result.scenarioName)}</strong>: ${escapeHtml(result.error)}</p>`
    ).join("");
}

function renderDifferences(changes, reference) {
    const entries = Object.entries(changes);
    if (!entries.length) return reference ? '<span class="reference-label">Reference values</span>' : "No differences";
    return `
        <details class="parameter-diff">
            <summary>${entries.length} changed parameter${entries.length === 1 ? "" : "s"}</summary>
            <ul>${entries.map(([path, change]) => `
                <li><strong>${escapeHtml(path)}</strong><span>${escapeHtml(compact(change.reference))} → ${escapeHtml(compact(change.value))}</span></li>
            `).join("")}</ul>
        </details>
    `;
}

function sortValue(result, key) {
    if (key === "confidence") {
        return result.correctedEvidence?.sustainabilityConfidence
            ?? result.originalEvidence?.sustainabilityConfidence;
    }
    if (key === "discarded") {
        return result.correctedEvidence?.discardedPerishables
            ?? result.originalEvidence?.discardedPerishables;
    }
    if (key === "mismatch") return result.finalMetrics?.aggregateMismatch;
    return result[key];
}

function compareValues(first, second) {
    if (first == null) return second == null ? 0 : 1;
    if (second == null) return -1;
    if (typeof first === "number" && typeof second === "number") return first - second;
    return String(first).localeCompare(String(second));
}

function formValues(form) {
    const values = {};
    for (const element of form.elements) {
        if (!element.name || element.disabled) continue;
        values[element.name] =
            element.type === "checkbox" ? String(element.checked) : element.value;
    }
    return values;
}

function contestationValues(form) {
    const values = {
        traceId: form.dataset.traceId,
        correctionType: form.elements.correctionType.value,
        reason: form.elements.reason.value
    };
    for (const toggle of form.querySelectorAll(".correction-toggle:checked")) {
        const target = form.elements[toggle.dataset.target];
        if (!target.disabled) values[target.name] = target.value;
    }
    return values;
}

function updateCorrectionControls() {
    const form = byId("contest-form");
    const prospective =
        form.elements.correctionType.value === "PROSPECTIVE_CONSENT_CHANGE";
    const historicalDefault =
        "One item spoiled before delivery and was not avoidable household waste.";
    const prospectiveDefault =
        "Grant the selected consent from this point forward without rewriting the source event.";
    if (prospective && form.elements.reason.value === historicalDefault) {
        form.elements.reason.value = prospectiveDefault;
    } else if (!prospective && form.elements.reason.value === prospectiveDefault) {
        form.elements.reason.value = historicalDefault;
    }
    for (const toggle of form.querySelectorAll(".correction-toggle")) {
        const numeric = ["correctedDiscardedPerishables", "correctedConfidence"]
            .includes(toggle.dataset.target);
        if (prospective && numeric) {
            toggle.checked = false;
            toggle.disabled = true;
        } else {
            toggle.disabled = false;
        }
        form.elements[toggle.dataset.target].disabled =
            !toggle.checked || toggle.disabled;
    }
}

function human(value) {
    return String(value || "")
        .replace(/([a-z0-9])([A-Z])/g, "$1 $2")
        .toLowerCase()
        .replaceAll("_", " ")
        .replace(/\b\w/g, letter => letter.toUpperCase());
}

function number(value) {
    return Number(value).toFixed(3);
}

function metric(value) {
    return value == null ? "—" : Number(value).toFixed(3);
}

function yesNo(value) {
    return value === true ? "Granted" : value === false ? "Not granted" : "—";
}

function valueTransition(original, corrected) {
    const first = original == null ? "—" : String(original);
    if (corrected === undefined || corrected === null || String(corrected) === first) {
        return escapeHtml(first);
    }
    return `${escapeHtml(first)} <b>→</b> ${escapeHtml(String(corrected))}`;
}

function compact(value) {
    const text = typeof value === "string" ? value : JSON.stringify(value);
    if (text == null) return "null";
    return text.length <= 80 ? text : `${text.slice(0, 77)}…`;
}

function shortId(id) {
    return id ? id.slice(0, 14) : "-";
}

function escapeHtml(value) {
    return String(value)
        .replaceAll("&", "&amp;")
        .replaceAll("<", "&lt;")
        .replaceAll(">", "&gt;")
        .replaceAll('"', "&quot;")
        .replaceAll("'", "&#039;");
}

function delay(milliseconds) {
    return new Promise(resolve => setTimeout(resolve, milliseconds));
}

let toastTimer;
function toast(message, error = false) {
    const element = byId("toast");
    element.textContent = message;
    element.className = `show${error ? " error" : ""}`;
    clearTimeout(toastTimer);
    toastTimer = setTimeout(() => element.className = "", 3500);
}

async function act(button, action, success) {
    button.disabled = true;
    try {
        await action();
        await refresh();
        toast(success);
    } catch (error) {
        toast(error.message, true);
    } finally {
        button.disabled = false;
    }
}

byId("configuration-editor").addEventListener("input", () => {
    configurationEditorDirty = true;
});

byId("load-configuration-example").addEventListener("click", () => {
    const selected = byId("configuration-example").value;
    const configuration = configurationExamples[selected];
    if (!configuration) return toast("Configuration example is unavailable", true);
    byId("configuration-editor").value = JSON.stringify(configuration, null, 2);
    configurationEditorDirty = true;
    toast("Example loaded into the editor; validate and apply when ready");
});

byId("configuration-file").addEventListener("change", async event => {
    const file = event.target.files[0];
    if (!file) return;
    try {
        const text = await file.text();
        JSON.parse(text);
        byId("configuration-editor").value = text;
        configurationEditorDirty = true;
        toast(`Loaded ${file.name} into the configuration editor`);
    } catch (error) {
        toast(`Invalid JSON file: ${error.message}`, true);
    } finally {
        event.target.value = "";
    }
});

byId("apply-configuration").addEventListener("click", event => {
    let parsed;
    try {
        parsed = JSON.parse(byId("configuration-editor").value);
    } catch (error) {
        return toast(`Invalid JSON syntax: ${error.message}`, true);
    }
    act(event.currentTarget, async () => {
        await requestJson("/api/configuration", parsed);
        configurationEditorDirty = false;
    }, "Configuration validated and applied to a fresh runtime");
});

byId("evidence-form").elements.sustainabilityConfidence.addEventListener("input", event => {
    byId("confidence-output").textContent = Number(event.target.value).toFixed(2);
});

byId("evidence-form").addEventListener("submit", event => {
    event.preventDefault();
    act(
        event.submitter,
        () => request("/api/cycle", formValues(event.currentTarget)),
        "EASE cycle completed"
    );
});

byId("llm-form").addEventListener("submit", event => {
    event.preventDefault();
    const form = event.currentTarget;
    const values = formValues(form);
    act(event.submitter, async () => {
        await request("/api/llm/configure", values);
        form.elements.apiKey.value = "";
    }, "LLM connector saved in process memory");
});

byId("test-llm").addEventListener("click", event => {
    const form = byId("llm-form");
    const values = formValues(form);
    act(event.currentTarget, async () => {
        await request("/api/llm/configure", values);
        form.elements.apiKey.value = "";
        const result = await request("/api/llm/test", {});
        toast(`Structured output verified with ${result.responseModel || "configured model"} in ${result.latencyMillis} ms`);
    }, "LLM structured-output connection verified");
});

byId("clear-llm-key").addEventListener("click", event =>
    act(
        event.currentTarget,
        () => request("/api/llm/clear-key", {}),
        "In-memory LLM API key cleared"
    ));

byId("llm-form").elements.protocol.addEventListener("change", event => {
    const endpoint = byId("llm-form").elements.endpoint;
    if (!endpoint.value.startsWith("https://api.openai.com/v1/")) return;
    endpoint.value = event.target.value === "RESPONSES"
        ? "https://api.openai.com/v1/responses"
        : "https://api.openai.com/v1/chat/completions";
});

byId("paper-scenario").addEventListener("click", event =>
    act(
        event.currentTarget,
        () => request("/api/scenarios/worked", {}),
        "Original worked example and default configuration reproduced"
    ));

byId("active-scenario").addEventListener("click", event =>
    act(
        event.currentTarget,
        () => request("/api/scenarios/paper", {}),
        "Active configuration default scenario executed"
    ));

byId("privacy-scenario").addEventListener("click", event =>
    act(
        event.currentTarget,
        () => request("/api/scenarios/privacy", {}),
        "Hard privacy safeguard executed"
    ));

byId("reset").addEventListener("click", event =>
    act(event.currentTarget, () => request("/api/reset", {}), "Runtime reset"));

byId("confirm-intention").addEventListener("click", event =>
    act(
        event.currentTarget,
        () => request("/api/confirm", { traceId: event.currentTarget.dataset.traceId }),
        "Intention confirmed and executed"
    ));

for (const toggle of byId("contest-form").querySelectorAll(".correction-toggle")) {
    toggle.addEventListener("change", updateCorrectionControls);
}
byId("correction-type").addEventListener("change", updateCorrectionControls);
updateCorrectionControls();

byId("contest-form").addEventListener("submit", event => {
    event.preventDefault();
    const values = contestationValues(event.currentTarget);
    if (!event.currentTarget.querySelector(".correction-toggle:checked")) {
        return toast("Select at least one value to contest", true);
    }
    act(
        event.submitter,
        () => request("/api/contest", values),
        "Contestation accepted and evaluated with the shared MAPE-K logic"
    );
});

byId("run-selected-batch").addEventListener("click", event => {
    try {
        startBatch(selectedBatchDefinition(), event.currentTarget);
    } catch (error) {
        toast(error.message, true);
    }
});

byId("load-example-batch").addEventListener("click", event =>
    startBatch(bundledBatch, event.currentTarget));

byId("batch-file").addEventListener("change", async event => {
    const file = event.target.files[0];
    if (!file) return;
    try {
        const definition = JSON.parse(await file.text());
        await startBatch(definition);
    } catch (error) {
        toast(`Cannot run batch JSON: ${error.message}`, true);
    } finally {
        event.target.value = "";
    }
});

byId("comparison-filter").addEventListener("input", renderComparison);
for (const button of document.querySelectorAll(".sort-button")) {
    button.addEventListener("click", () => {
        const key = button.dataset.sort;
        comparisonSort = comparisonSort.key === key
            ? { key, direction: comparisonSort.direction * -1 }
            : { key, direction: 1 };
        renderComparison();
    });
}

Promise.all([refresh(), loadCatalogues()])
    .catch(error => toast(error.message, true));
