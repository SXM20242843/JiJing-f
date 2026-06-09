#!/usr/bin/env node

const fs = require('fs');
const path = require('path');
const os = require('os');
const { spawnSync } = require('child_process');

const ROOT_DIR = process.cwd();
const STEP_RESULTS = [];
const RUN_STARTED_AT = new Date();

main().catch((error) => {
  console.error('[fatal]', error && error.stack ? error.stack : error);
  process.exitCode = 1;
});

async function main() {
  const envFile = loadEnvFile(path.join(ROOT_DIR, '.env'));
  const args = parseArgs(process.argv.slice(2));
  const config = buildConfig(args, envFile);
  const db = buildDbClient(config);

  printConfig(config, db);

  let routeCountBeforeSpotExplain = null;
  let routeCountAfterSpotExplain = null;
  let routeCountAfterRouteIntent = null;

  if (db.available) {
    console.log('\n[db] mysql cli detected; database checks will run when visitId is available.');
  } else {
    console.log('\n[db] database auto-check disabled:', db.reason);
  }

  const sessionId = `smoke_visit_${Date.now()}`;
  let visitId = normalizeId(config.VISIT_ID);
  let routePlanId = '';

  const startBody = compactObject({
    userId: config.USER_ID,
    user_id: config.USER_ID,
    areaId: asNumberOrString(config.AREA_ID),
    area_id: asNumberOrString(config.AREA_ID),
    areaCode: config.AREA_CODE,
    area_code: config.AREA_CODE,
    parkId: asNumberOrString(firstFilled(config.AREA_ID, config.AREA_CODE)),
    park_id: asNumberOrString(firstFilled(config.AREA_ID, config.AREA_CODE)),
    parkCode: config.AREA_CODE,
    park_code: config.AREA_CODE,
    areaName: config.PARK_NAME,
    area_name: config.PARK_NAME,
    parkName: config.PARK_NAME,
    park_name: config.PARK_NAME,
    groupSize: config.GROUP_SIZE,
    group_size: config.GROUP_SIZE,
    travelPeopleCount: config.GROUP_SIZE,
    travel_people_count: config.GROUP_SIZE,
    travelType: config.TRAVEL_TYPE,
    travel_type: config.TRAVEL_TYPE,
    visitPreference: config.VISIT_PREFERENCE,
    visit_preference: config.VISIT_PREFERENCE,
    travelPreference: config.VISIT_PREFERENCE,
    travel_preference: config.VISIT_PREFERENCE,
    estimatedDuration: config.ESTIMATED_DURATION,
    estimated_duration: config.ESTIMATED_DURATION,
    startSource: 'smoke-test',
    start_source: 'smoke-test',
    entrySource: 'smoke-test',
    entry_source: 'smoke-test',
    latitude: config.LATITUDE,
    longitude: config.LONGITUDE
  });

  const startStep = await apiStep(config, {
    name: '1. POST /api/app/visit/start',
    method: 'POST',
    path: '/api/app/visit/start',
    body: startBody,
    validate: ({ json, httpOk }) => {
      const extracted = extractVisitId(json);
      return {
        pass: httpOk && isBusinessOk(json) && !!extracted,
        detail: extracted ? `visitId=${extracted}` : 'visitId missing'
      };
    }
  });

  visitId = normalizeId(extractVisitId(startStep.json) || visitId);
  if (!visitId) {
    printSummary(db, config, null, {
      routeCountBeforeSpotExplain,
      routeCountAfterSpotExplain,
      routeCountAfterRouteIntent
    });
    process.exitCode = 1;
    return;
  }

  routeCountBeforeSpotExplain = db.available ? db.countRoutePlans(visitId) : null;

  const spotEnterBody = compactObject({
    visitId,
    visit_id: visitId,
    userId: config.USER_ID,
    user_id: config.USER_ID,
    sessionId,
    session_id: sessionId,
    areaId: asNumberOrString(config.AREA_ID),
    area_id: asNumberOrString(config.AREA_ID),
    areaCode: config.AREA_CODE,
    area_code: config.AREA_CODE,
    parkName: config.PARK_NAME,
    park_name: config.PARK_NAME,
    scenicId: config.SPOT_ID,
    scenic_id: config.SPOT_ID,
    spotId: config.SPOT_ID,
    spot_id: config.SPOT_ID,
    scenicName: config.SPOT_NAME,
    scenic_name: config.SPOT_NAME,
    spotName: config.SPOT_NAME,
    spot_name: config.SPOT_NAME,
    enterSource: 'smoke-test',
    enter_source: 'smoke-test',
    locationSource: 'smoke-test',
    location_source: 'smoke-test',
    trigger: 'smoke-spot-enter',
    source: 'smoke-test',
    demoMode: true,
    demo_mode: true,
    latitude: config.LATITUDE,
    longitude: config.LONGITUDE
  });

  await apiStep(config, {
    name: '2. POST /api/app/visit/spot/enter',
    method: 'POST',
    path: '/api/app/visit/spot/enter',
    body: spotEnterBody,
    validate: ({ json, httpOk }) => ({
      pass: httpOk && isBusinessOk(json),
      detail: isBusinessOk(json) ? 'spot enter accepted' : businessMessage(json)
    })
  });

  const spotExplainBody = buildGuideChatBody(config, {
    visitId,
    sessionId,
    question: `请讲解一下【${config.SPOT_NAME}】`,
    requestType: 'spot_explain',
    route: false,
    routeIntent: false,
    suppressRoute: true
  });

  const spotExplainStep = await apiStep(config, {
    name: '3. POST /api/guide/chat spot_explain suppressRoute=true',
    method: 'POST',
    path: '/api/guide/chat',
    body: spotExplainBody,
    validate: ({ json, httpOk }) => {
      const hasRoute = hasRoutePayload(json);
      return {
        pass: httpOk && isBusinessOk(json) && !hasRoute,
        detail: hasRoute ? 'response contains route payload' : 'response has no route payload'
      };
    }
  });

  routeCountAfterSpotExplain = db.available ? db.countRoutePlans(visitId) : null;
  if (db.available) {
    const unchanged = routeCountAfterSpotExplain === routeCountBeforeSpotExplain;
    addDbAssertion(
      'tourist_route_plan unchanged after spot_explain suppressRoute=true',
      unchanged,
      `before=${routeCountBeforeSpotExplain}, after=${routeCountAfterSpotExplain}`
    );
    if (!unchanged) {
      spotExplainStep.pass = false;
      spotExplainStep.detail += `; DB route count changed ${routeCountBeforeSpotExplain} -> ${routeCountAfterSpotExplain}`;
    }
  }

  const routeIntentBody = buildGuideChatBody(config, {
    visitId,
    sessionId,
    question: '请为我推荐一条适合本次游玩的路线',
    requestType: 'route_recommend',
    route: true,
    routeIntent: true,
    suppressRoute: false
  });

  const routeIntentStep = await apiStep(config, {
    name: '4. POST /api/guide/chat routeIntent=true',
    method: 'POST',
    path: '/api/guide/chat',
    body: routeIntentBody,
    validate: ({ json, httpOk }) => {
      const hasRoute = hasRoutePayload(json);
      return {
        pass: httpOk && isBusinessOk(json) && hasRoute,
        detail: hasRoute ? 'response contains route payload' : 'response does not contain route payload'
      };
    }
  });

  routePlanId = findFirstDeep(routeIntentStep.json, [
    'planId',
    'plan_id',
    'routePlanId',
    'route_plan_id',
    'id'
  ]);
  routeCountAfterRouteIntent = db.available ? db.countRoutePlans(visitId) : null;
  if (db.available) {
    const increased = Number(routeCountAfterRouteIntent) > Number(routeCountAfterSpotExplain);
    addDbAssertion(
      'tourist_route_plan increased after routeIntent=true',
      increased,
      `before=${routeCountAfterSpotExplain}, after=${routeCountAfterRouteIntent}`
    );
    if (!increased) {
      routeIntentStep.pass = false;
      routeIntentStep.detail += `; DB route count did not increase ${routeCountAfterSpotExplain} -> ${routeCountAfterRouteIntent}`;
    }
  }

  await behaviorEventStep(config, {
    stepName: '5. POST /api/app/behavior/event map_card_show',
    eventType: 'map_card_show',
    visitId,
    sessionId,
    routePlanId
  });
  await behaviorEventStep(config, {
    stepName: '6. POST /api/app/behavior/event navigation_start',
    eventType: 'navigation_start',
    visitId,
    sessionId,
    routePlanId
  });
  await behaviorEventStep(config, {
    stepName: '7. POST /api/app/behavior/event route_spot_click',
    eventType: 'route_spot_click',
    visitId,
    sessionId,
    routePlanId
  });

  const spotLeaveBody = compactObject({
    visitId,
    visit_id: visitId,
    userId: config.USER_ID,
    user_id: config.USER_ID,
    sessionId,
    session_id: sessionId,
    areaId: asNumberOrString(config.AREA_ID),
    area_id: asNumberOrString(config.AREA_ID),
    parkName: config.PARK_NAME,
    park_name: config.PARK_NAME,
    scenicId: config.SPOT_ID,
    scenic_id: config.SPOT_ID,
    spotId: config.SPOT_ID,
    spot_id: config.SPOT_ID,
    scenicName: config.SPOT_NAME,
    scenic_name: config.SPOT_NAME,
    spotName: config.SPOT_NAME,
    spot_name: config.SPOT_NAME,
    leaveSource: 'smoke-test',
    leave_source: 'smoke-test',
    locationSource: 'smoke-test',
    location_source: 'smoke-test',
    trigger: 'smoke-spot-leave',
    source: 'smoke-test',
    demoMode: true,
    demo_mode: true,
    latitude: config.LATITUDE,
    longitude: config.LONGITUDE
  });

  await apiStep(config, {
    name: '8. POST /api/app/visit/spot/leave',
    method: 'POST',
    path: '/api/app/visit/spot/leave',
    body: spotLeaveBody,
    validate: ({ json, httpOk }) => ({
      pass: httpOk && isBusinessOk(json),
      detail: isBusinessOk(json) ? 'spot leave accepted' : businessMessage(json)
    })
  });

  const endBody = compactObject({
    visitId,
    visit_id: visitId,
    userId: config.USER_ID,
    user_id: config.USER_ID,
    sessionId,
    session_id: sessionId,
    areaId: asNumberOrString(config.AREA_ID),
    area_id: asNumberOrString(config.AREA_ID),
    parkName: config.PARK_NAME,
    park_name: config.PARK_NAME,
    spotId: config.SPOT_ID,
    spot_id: config.SPOT_ID,
    spotName: config.SPOT_NAME,
    spot_name: config.SPOT_NAME,
    endSource: 'smoke-test',
    end_source: 'smoke-test',
    endReason: 'SMOKE_TEST_END',
    end_reason: 'SMOKE_TEST_END',
    locationSource: 'smoke-test',
    location_source: 'smoke-test',
    trigger: 'smoke-visit-end',
    source: 'smoke-test',
    demoMode: true,
    demo_mode: true,
    latitude: config.LATITUDE,
    longitude: config.LONGITUDE
  });

  await apiStep(config, {
    name: '9. POST /api/app/visit/end',
    method: 'POST',
    path: '/api/app/visit/end',
    body: endBody,
    validate: ({ json, httpOk }) => {
      const ended = isEndedResponse(json);
      return {
        pass: httpOk && isBusinessOk(json) && ended,
        detail: ended ? 'visit end confirmed' : `not ended: ${businessMessage(json)}`
      };
    }
  });

  await apiStep(config, {
    name: '10. GET /api/app/visit/status',
    method: 'GET',
    path: '/api/app/visit/status',
    query: compactObject({
      visitId,
      areaId: config.AREA_ID,
      area_id: config.AREA_ID,
      userId: config.USER_ID,
      user_id: config.USER_ID
    }),
    validate: ({ json, httpOk }) => {
      const data = unwrapData(json);
      const hasRunningVisit = readBool(data, ['hasRunningVisit', 'has_running_visit']);
      const status = normalizeStatus(firstFilled(
        data && data.status,
        data && data.visitStatus,
        data && data.visit_status,
        data && data.state
      ));
      const pass = httpOk && isBusinessOk(json) && hasRunningVisit !== true && status === 'COMPLETED';
      return {
        pass,
        detail: `hasRunningVisit=${hasRunningVisit}, status=${status || '(empty)'}, lastReportVisitId=${firstFilled(data && data.lastReportVisitId, data && data.last_report_visit_id)}`
      };
    }
  });

  await apiStep(config, {
    name: '11. GET /api/app/visit/report/detail',
    method: 'GET',
    path: '/api/app/visit/report/detail',
    query: compactObject({
      visitId,
      userId: config.USER_ID,
      user_id: config.USER_ID
    }),
    validate: ({ json, httpOk }) => {
      const data = unwrapData(json);
      const hasData = data && typeof data === 'object' && Object.keys(data).length > 0;
      const reportVisitId = normalizeId(firstFilled(
        data && data.visitId,
        data && data.visit_id,
        data && data.id
      ));
      return {
        pass: httpOk && isBusinessOk(json) && hasData && (!reportVisitId || reportVisitId === visitId),
        detail: hasData ? `reportVisitId=${reportVisitId || '(not in response)'}` : 'empty report data'
      };
    }
  });

  if (db.available) {
    runDbChecks(db, visitId, {
      routeCountBeforeSpotExplain,
      routeCountAfterSpotExplain,
      routeCountAfterRouteIntent
    });
  }

  printSummary(db, config, visitId, {
    routeCountBeforeSpotExplain,
    routeCountAfterSpotExplain,
    routeCountAfterRouteIntent
  });
}

async function behaviorEventStep(config, options) {
  const body = compactObject({
    eventType: options.eventType,
    event_type: options.eventType,
    eventName: options.eventType,
    event_name: options.eventType,
    userId: config.USER_ID,
    user_id: config.USER_ID,
    sessionId: options.sessionId,
    session_id: options.sessionId,
    visitId: options.visitId,
    visit_id: options.visitId,
    areaId: asNumberOrString(config.AREA_ID),
    area_id: asNumberOrString(config.AREA_ID),
    areaCode: config.AREA_CODE,
    area_code: config.AREA_CODE,
    spotId: config.SPOT_ID,
    spot_id: config.SPOT_ID,
    sceneCode: config.SPOT_ID,
    scene_code: config.SPOT_ID,
    spotName: config.SPOT_NAME,
    spot_name: config.SPOT_NAME,
    parkName: config.PARK_NAME,
    park_name: config.PARK_NAME,
    planId: options.routePlanId,
    plan_id: options.routePlanId,
    routePlanId: options.routePlanId,
    route_plan_id: options.routePlanId,
    routeName: 'smoke route',
    route_name: 'smoke route',
    entityType: options.eventType === 'route_spot_click' ? 'SPOT' : 'ROUTE',
    entity_type: options.eventType === 'route_spot_click' ? 'SPOT' : 'ROUTE',
    entityId: options.routePlanId || config.SPOT_ID || options.visitId,
    entity_id: options.routePlanId || config.SPOT_ID || options.visitId,
    source: 'smoke-test',
    sourcePage: 'smoke-test',
    source_page: 'smoke-test',
    clientType: 'SMOKE',
    client_type: 'SMOKE',
    trigger: 'smoke-test',
    demoMode: true,
    demo_mode: true,
    latitude: config.LATITUDE,
    longitude: config.LONGITUDE,
    extra: {
      smokeTest: true,
      routePlanId: options.routePlanId || '',
      runStartedAt: RUN_STARTED_AT.toISOString()
    }
  });

  return apiStep(config, {
    name: options.stepName,
    method: 'POST',
    path: '/api/app/behavior/event',
    body,
    validate: ({ json, httpOk }) => ({
      pass: httpOk && isBusinessOk(json),
      detail: isBusinessOk(json) ? `${options.eventType} accepted` : businessMessage(json)
    })
  });
}

function buildGuideChatBody(config, options) {
  const routeIntent = !!options.routeIntent;
  const suppressRoute = !!options.suppressRoute;
  return compactObject({
    sessionId: options.sessionId,
    session_id: options.sessionId,
    conversationId: options.sessionId,
    conversation_id: options.sessionId,
    userId: config.USER_ID,
    user_id: config.USER_ID,
    loginUserId: config.USER_ID,
    login_user_id: config.USER_ID,
    visitId: options.visitId,
    visit_id: options.visitId,
    mode: 'onsite',
    question: options.question,
    requestType: options.requestType,
    request_type: options.requestType,
    route: !!options.route,
    routeIntent,
    route_intent: routeIntent,
    suppressRoute,
    suppress_route: suppressRoute,
    needVoice: false,
    need_voice: false,
    enableTts: false,
    enable_tts: false,
    enableContext: true,
    enable_context: true,
    inputType: 'text',
    input_type: 'text',
    parkId: firstFilled(config.AREA_CODE, config.AREA_ID),
    park_id: firstFilled(config.AREA_CODE, config.AREA_ID),
    parkName: config.PARK_NAME,
    park_name: config.PARK_NAME,
    areaId: asNumberOrString(config.AREA_ID),
    area_id: asNumberOrString(config.AREA_ID),
    areaCode: config.AREA_CODE,
    area_code: config.AREA_CODE,
    areaName: config.PARK_NAME,
    area_name: config.PARK_NAME,
    scenicId: config.SPOT_ID,
    scenic_id: config.SPOT_ID,
    scenicName: config.SPOT_NAME,
    scenic_name: config.SPOT_NAME,
    spotId: config.SPOT_ID,
    spot_id: config.SPOT_ID,
    spotName: config.SPOT_NAME,
    spot_name: config.SPOT_NAME,
    currentSpotId: routeIntent ? '' : config.SPOT_ID,
    current_spot_id: routeIntent ? '' : config.SPOT_ID,
    currentSpotName: routeIntent ? '' : config.SPOT_NAME,
    current_spot_name: routeIntent ? '' : config.SPOT_NAME,
    routeStartType: 'park_entrance',
    route_start_type: 'park_entrance',
    startSpotId: '',
    start_spot_id: '',
    startSpotName: '景区入口',
    start_spot_name: '景区入口',
    groupSize: config.GROUP_SIZE,
    group_size: config.GROUP_SIZE,
    travelType: config.TRAVEL_TYPE,
    travel_type: config.TRAVEL_TYPE,
    visitPreference: config.VISIT_PREFERENCE,
    visit_preference: config.VISIT_PREFERENCE,
    availableMinutes: Number(config.ESTIMATED_DURATION) || undefined,
    available_minutes: Number(config.ESTIMATED_DURATION) || undefined,
    latitude: config.LATITUDE,
    longitude: config.LONGITUDE,
    clientContext: {
      page: 'guide-smoke-test',
      requestType: options.requestType,
      routeIntent,
      route_intent: routeIntent,
      suppressRoute,
      suppress_route: suppressRoute,
      routeTrigger: routeIntent ? 'manual' : '',
      route_trigger: routeIntent ? 'manual' : '',
      smokeTest: true
    },
    client_context: {
      page: 'guide-smoke-test',
      requestType: options.requestType,
      routeIntent,
      route_intent: routeIntent,
      suppressRoute,
      suppress_route: suppressRoute,
      routeTrigger: routeIntent ? 'manual' : '',
      route_trigger: routeIntent ? 'manual' : '',
      smokeTest: true
    }
  });
}

async function apiStep(config, options) {
  const url = buildUrl(config.BASE_URL, options.path, options.query);
  const body = options.body || null;
  const headers = {
    Accept: 'application/json'
  };
  if (body) {
    headers['Content-Type'] = 'application/json; charset=UTF-8';
  }
  if (config.TOKEN) {
    headers.Authorization = buildAuthorization(config.TOKEN);
  }

  console.log(`\n=== ${options.name} ===`);
  console.log('URL:', url);
  console.log('Request body:', body ? pretty(body) : '(none)');

  let responseText = '';
  let status = 0;
  let json = null;
  let httpOk = false;
  let error = null;

  try {
    const response = await fetch(url, {
      method: options.method,
      headers,
      body: body ? JSON.stringify(body) : undefined
    });
    status = response.status;
    httpOk = response.ok;
    responseText = await response.text();
    json = parseJson(responseText);
  } catch (err) {
    error = err;
    responseText = err && err.message ? err.message : String(err);
  }

  console.log('HTTP status:', status || '(request failed)');
  console.log('Response body:', json ? pretty(json) : responseText || '(empty)');

  let pass = false;
  let detail = '';
  if (error) {
    detail = error.message || String(error);
  } else if (typeof options.validate === 'function') {
    const validated = options.validate({ json, text: responseText, httpOk, status });
    pass = !!validated.pass;
    detail = validated.detail || '';
  } else {
    pass = httpOk && isBusinessOk(json);
    detail = businessMessage(json);
  }

  console.log('Pass:', pass ? 'YES' : 'NO');
  if (detail) {
    console.log('Detail:', detail);
  }

  const result = {
    name: options.name,
    url,
    status,
    pass,
    detail,
    json,
    responseText
  };
  STEP_RESULTS.push(result);
  return result;
}

function runDbChecks(db, visitId, routeCounts) {
  console.log('\n=== DB checks ===');

  const sessionRows = db.queryRows(`
    SELECT id, visit_status, IFNULL(DATE_FORMAT(end_time, '%Y-%m-%d %H:%i:%s'), '') AS end_time
    FROM tourist_visit_session
    WHERE id = ${sqlNumber(visitId)}
  `);
  const session = sessionRows[0] || [];
  addDbAssertion(
    'tourist_visit_session end_time is not empty',
    !!session[2],
    `visit_status=${session[1] || '(missing)'}, end_time=${session[2] || '(empty)'}`
  );
  addDbAssertion(
    'tourist_visit_session visit_status is COMPLETED',
    normalizeStatus(session[1]) === 'COMPLETED',
    `visit_status=${session[1] || '(missing)'}`
  );

  const spotRows = db.queryRows(`
    SELECT
      COUNT(1) AS total_count,
      SUM(CASE WHEN enter_time IS NOT NULL THEN 1 ELSE 0 END) AS enter_count,
      SUM(CASE WHEN leave_time IS NOT NULL THEN 1 ELSE 0 END) AS leave_count,
      SUM(CASE WHEN stay_seconds IS NOT NULL THEN 1 ELSE 0 END) AS stay_count
    FROM tourist_spot_visit_record
    WHERE visit_id = ${sqlNumber(visitId)}
  `);
  const spot = spotRows[0] || [];
  addDbAssertion(
    'tourist_spot_visit_record has enter_time / leave_time / stay_seconds',
    Number(spot[0]) > 0 && Number(spot[1]) > 0 && Number(spot[2]) > 0 && Number(spot[3]) > 0,
    `total=${spot[0] || 0}, enter=${spot[1] || 0}, leave=${spot[2] || 0}, stay=${spot[3] || 0}`
  );

  addDbAssertion(
    'tourist_route_plan no insert after suppressRoute spot_explain',
    routeCounts.routeCountBeforeSpotExplain === routeCounts.routeCountAfterSpotExplain,
    `before=${routeCounts.routeCountBeforeSpotExplain}, after=${routeCounts.routeCountAfterSpotExplain}`
  );
  addDbAssertion(
    'tourist_route_plan inserted after routeIntent',
    Number(routeCounts.routeCountAfterRouteIntent) > Number(routeCounts.routeCountAfterSpotExplain),
    `before=${routeCounts.routeCountAfterSpotExplain}, after=${routeCounts.routeCountAfterRouteIntent}`
  );

  const eventRows = db.queryRows(`
    SELECT event_type, COUNT(1)
    FROM tourist_behavior_event
    WHERE visit_id = ${sqlNumber(visitId)}
      AND event_type IN (
        'visit_start',
        'spot_enter',
        'spot_leave',
        'map_card_show',
        'navigation_start',
        'route_spot_click',
        'visit_end'
      )
    GROUP BY event_type
  `);
  const eventCounts = Object.fromEntries(eventRows.map((row) => [row[0], Number(row[1])]));
  for (const eventName of ['map_card_show', 'navigation_start', 'route_spot_click', 'visit_end']) {
    addDbAssertion(
      `tourist_behavior_event has ${eventName}`,
      Number(eventCounts[eventName] || 0) > 0,
      `${eventName}=${eventCounts[eventName] || 0}`
    );
  }
  console.log('Behavior event counts:', pretty(eventCounts));
}

function addDbAssertion(name, pass, detail) {
  console.log(`${pass ? '[PASS]' : '[FAIL]'} ${name}${detail ? ` - ${detail}` : ''}`);
  STEP_RESULTS.push({
    name: `DB: ${name}`,
    status: 'DB',
    pass,
    detail,
    json: null,
    responseText: ''
  });
}

function printSummary(db, config, visitId, routeCounts) {
  console.log('\n=== Summary ===');
  for (const item of STEP_RESULTS) {
    console.log(`${item.pass ? '[PASS]' : '[FAIL]'} ${item.name}${item.detail ? ` - ${item.detail}` : ''}`);
  }

  if (!db.available) {
    printManualSql(config, visitId, routeCounts);
  }

  const failed = STEP_RESULTS.filter((item) => !item.pass);
  console.log('\nOverall:', failed.length === 0 ? 'PASS' : `FAIL (${failed.length} failed)`);
  if (failed.length > 0) {
    console.log('First failed step:', failed[0].name);
    process.exitCode = 1;
  }
}

function printManualSql(config, visitId, routeCounts) {
  const id = visitId ? sqlNumber(visitId) : '<VISIT_ID>';
  console.log('\n=== Manual SQL (database auto-check unavailable) ===');
  console.log('-- Run these after the smoke test. Replace <VISIT_ID> if the script could not create one.');
  console.log(`SELECT id, visit_status, end_time, total_duration_seconds FROM tourist_visit_session WHERE id = ${id};`);
  console.log(`SELECT id, visit_id, frontend_scenic_id, frontend_scenic_name, enter_time, leave_time, stay_seconds FROM tourist_spot_visit_record WHERE visit_id = ${id} ORDER BY id DESC;`);
  console.log(`SELECT COUNT(1) AS route_plan_count FROM tourist_route_plan WHERE visit_id = ${id};`);
  console.log(`SELECT id, plan_no, route_name, plan_status FROM tourist_route_plan WHERE visit_id = ${id} ORDER BY id DESC;`);
  console.log(`SELECT plan_id, node_name, sort_order FROM tourist_route_plan_node WHERE plan_id IN (SELECT id FROM tourist_route_plan WHERE visit_id = ${id}) ORDER BY plan_id, sort_order;`);
  console.log(`SELECT event_type, COUNT(1) AS cnt FROM tourist_behavior_event WHERE visit_id = ${id} AND event_type IN ('visit_start','spot_enter','spot_leave','map_card_show','navigation_start','route_spot_click','visit_end') GROUP BY event_type;`);
  console.log(`SELECT * FROM chat_session WHERE user_id = '${escapeSqlString(config.USER_ID)}' ORDER BY id DESC LIMIT 3;`);
  console.log(`SELECT id, session_id, role, content, create_time FROM chat_message WHERE session_id LIKE 'smoke_visit_%' ORDER BY id DESC LIMIT 20;`);
  if (routeCounts.routeCountBeforeSpotExplain !== null) {
    console.log(`-- Captured route counts: before spot_explain=${routeCounts.routeCountBeforeSpotExplain}, after spot_explain=${routeCounts.routeCountAfterSpotExplain}, after routeIntent=${routeCounts.routeCountAfterRouteIntent}`);
  }
}

function buildDbClient(config) {
  if (config.SKIP_DB) {
    return { available: false, reason: 'SKIP_DB=true' };
  }

  const dbConfig = resolveDbConfig(config);
  if (!dbConfig.host || !dbConfig.database || !dbConfig.user) {
    return {
      available: false,
      reason: 'missing DB_HOST/DB_NAME/DB_USER or DB_URL'
    };
  }

  const mysqlPath = findMysqlCli();
  if (!mysqlPath) {
    return {
      available: false,
      reason: 'mysql cli not found in PATH'
    };
  }

  const run = (sql) => {
    const args = [
      '-h', dbConfig.host,
      '-P', String(dbConfig.port || 3306),
      '-u', dbConfig.user,
      '--batch',
      '--raw',
      '--skip-column-names',
      dbConfig.database,
      '-e',
      oneLineSql(sql)
    ];
    const env = { ...process.env };
    if (dbConfig.password) {
      env.MYSQL_PWD = dbConfig.password;
    }
    const result = spawnSync(mysqlPath, args, {
      encoding: 'utf8',
      env,
      maxBuffer: 1024 * 1024 * 10
    });
    if (result.status !== 0) {
      throw new Error((result.stderr || result.stdout || `mysql exit ${result.status}`).trim());
    }
    return (result.stdout || '').trim();
  };

  try {
    run('SELECT 1');
  } catch (error) {
    return {
      available: false,
      reason: `mysql connection failed: ${error.message}`
    };
  }

  return {
    available: true,
    reason: '',
    countRoutePlans(visitId) {
      const value = run(`SELECT COUNT(1) FROM tourist_route_plan WHERE visit_id = ${sqlNumber(visitId)}`);
      return Number(value || 0);
    },
    queryRows(sql) {
      const output = run(sql);
      if (!output) return [];
      return output.split(/\r?\n/).map((line) => line.split('\t'));
    }
  };
}

function resolveDbConfig(config) {
  const ymlConfig = parseSpringDatasourceConfig();
  const url = firstFilled(config.DB_URL, config.JDBC_URL, config.SPRING_DATASOURCE_URL, ymlConfig.url);
  const parsed = parseJdbcMysqlUrl(url);
  return {
    host: firstFilled(config.DB_HOST, parsed.host),
    port: firstFilled(config.DB_PORT, parsed.port, '3306'),
    database: firstFilled(config.DB_NAME, config.DB_DATABASE, parsed.database),
    user: firstFilled(config.DB_USER, config.DB_USERNAME, config.SPRING_DATASOURCE_USERNAME, ymlConfig.username),
    password: firstFilled(config.DB_PASSWORD, config.DB_PASS, config.SPRING_DATASOURCE_PASSWORD, ymlConfig.password)
  };
}

function parseSpringDatasourceConfig() {
  const file = path.join(ROOT_DIR, 'scenic-ai-guid-backend1', 'backend', 'src', 'main', 'resources', 'application.yml');
  if (!fs.existsSync(file)) return {};
  const text = fs.readFileSync(file, 'utf8');
  const url = matchValue(text, /^\s*url:\s*(jdbc:mysql:[^\r\n]+)\s*$/m);
  const username = matchValue(text, /^\s*username:\s*([^\r\n]+)\s*$/m);
  const password = matchValue(text, /^\s*password:\s*([^\r\n]+)\s*$/m);
  return {
    url: stripQuotes(url),
    username: stripQuotes(username),
    password: stripQuotes(password)
  };
}

function findMysqlCli() {
  const cmd = process.platform === 'win32' ? 'where.exe' : 'which';
  const result = spawnSync(cmd, ['mysql'], { encoding: 'utf8' });
  const first = (result.stdout || '').split(/\r?\n/).map((line) => line.trim()).find(Boolean);
  return first || '';
}

function buildConfig(args, envFile) {
  const source = (name, fallback = '') => {
    const cliKey = normalizeArgKey(name);
    return firstFilled(args[cliKey], process.env[name], envFile[name], fallback);
  };

  return {
    BASE_URL: trimTrailingSlash(source('BASE_URL', 'http://127.0.0.1:8080')),
    TOKEN: source('TOKEN', ''),
    USER_ID: source('USER_ID', ''),
    VISIT_ID: source('VISIT_ID', ''),
    AREA_ID: source('AREA_ID', '1'),
    AREA_CODE: source('AREA_CODE', 'lingshan'),
    PARK_NAME: source('PARK_NAME', '灵山胜境'),
    SPOT_ID: source('SPOT_ID', 'lingshan_buddha'),
    SPOT_NAME: source('SPOT_NAME', '灵山大佛'),
    GROUP_SIZE: source('GROUP_SIZE', '2人'),
    TRAVEL_TYPE: source('TRAVEL_TYPE', '朋友游'),
    VISIT_PREFERENCE: source('VISIT_PREFERENCE', '深度文化'),
    ESTIMATED_DURATION: source('ESTIMATED_DURATION', '120'),
    LATITUDE: source('LATITUDE', ''),
    LONGITUDE: source('LONGITUDE', ''),
    DB_URL: source('DB_URL', ''),
    JDBC_URL: source('JDBC_URL', ''),
    SPRING_DATASOURCE_URL: source('SPRING_DATASOURCE_URL', ''),
    DB_HOST: source('DB_HOST', ''),
    DB_PORT: source('DB_PORT', ''),
    DB_NAME: source('DB_NAME', ''),
    DB_DATABASE: source('DB_DATABASE', ''),
    DB_USER: source('DB_USER', ''),
    DB_USERNAME: source('DB_USERNAME', ''),
    SPRING_DATASOURCE_USERNAME: source('SPRING_DATASOURCE_USERNAME', ''),
    DB_PASSWORD: source('DB_PASSWORD', ''),
    DB_PASS: source('DB_PASS', ''),
    SPRING_DATASOURCE_PASSWORD: source('SPRING_DATASOURCE_PASSWORD', ''),
    SKIP_DB: isTruthy(source('SKIP_DB', args.skipDb ? 'true' : 'false'))
  };
}

function printConfig(config, db) {
  console.log('=== visit-flow smoke test ===');
  console.log('BASE_URL:', config.BASE_URL);
  console.log('TOKEN:', config.TOKEN ? '(provided)' : '(empty)');
  console.log('USER_ID:', config.USER_ID || '(empty)');
  console.log('AREA_ID:', config.AREA_ID || '(empty)');
  console.log('AREA_CODE:', config.AREA_CODE || '(empty)');
  console.log('PARK_NAME:', config.PARK_NAME || '(empty)');
  console.log('SPOT_ID:', config.SPOT_ID || '(empty)');
  console.log('SPOT_NAME:', config.SPOT_NAME || '(empty)');
  console.log('DB:', db.available ? '(will connect)' : '(not connected)');
  if (!config.TOKEN) {
    console.log('[warn] TOKEN is empty. App endpoints may fail unless USER_ID is a valid non-temporary login user.');
  }
}

function loadEnvFile(file) {
  if (!fs.existsSync(file)) return {};
  const result = {};
  const content = fs.readFileSync(file, 'utf8');
  for (const line of content.split(/\r?\n/)) {
    const trimmed = line.trim();
    if (!trimmed || trimmed.startsWith('#')) continue;
    const eq = trimmed.indexOf('=');
    if (eq <= 0) continue;
    const key = trimmed.slice(0, eq).trim();
    const value = stripQuotes(trimmed.slice(eq + 1).trim());
    if (key) result[key] = value;
  }
  return result;
}

function parseArgs(argv) {
  const result = {};
  for (let index = 0; index < argv.length; index += 1) {
    const arg = argv[index];
    if (arg === '--skip-db' || arg === '--no-db') {
      result.skipDb = true;
      continue;
    }
    if (arg.startsWith('--')) {
      const raw = arg.slice(2);
      const eq = raw.indexOf('=');
      if (eq >= 0) {
        result[normalizeArgKey(raw.slice(0, eq))] = raw.slice(eq + 1);
      } else {
        const next = argv[index + 1];
        if (next && !next.startsWith('--')) {
          result[normalizeArgKey(raw)] = next;
          index += 1;
        } else {
          result[normalizeArgKey(raw)] = 'true';
        }
      }
      continue;
    }
    const eq = arg.indexOf('=');
    if (eq > 0) {
      result[normalizeArgKey(arg.slice(0, eq))] = arg.slice(eq + 1);
    }
  }
  return result;
}

function normalizeArgKey(key) {
  return String(key || '').trim().replace(/-/g, '_').toUpperCase();
}

function buildUrl(baseUrl, apiPath, query) {
  const url = new URL(trimTrailingSlash(baseUrl) + apiPath);
  for (const [key, value] of Object.entries(query || {})) {
    if (value !== undefined && value !== null && value !== '') {
      url.searchParams.set(key, String(value));
    }
  }
  return url.toString();
}

function buildAuthorization(token) {
  const text = String(token || '').trim();
  if (!text) return '';
  return text.toLowerCase().startsWith('bearer ') ? text : `Bearer ${text}`;
}

function isBusinessOk(json) {
  if (!json || typeof json !== 'object') return false;
  if (Object.prototype.hasOwnProperty.call(json, 'success')) {
    return json.success === true || json.success === 'true';
  }
  if (Object.prototype.hasOwnProperty.call(json, 'code')) {
    const code = Number(json.code);
    return code === 0 || code === 200;
  }
  if (Object.prototype.hasOwnProperty.call(json, 'status')) {
    const status = String(json.status || '').toUpperCase();
    if (['FAIL', 'FAILED', 'ERROR', 'UNAUTHORIZED'].includes(status)) return false;
  }
  return true;
}

function isEndedResponse(json) {
  const data = unwrapData(json);
  const status = normalizeStatus(firstFilled(
    data && data.status,
    data && data.visitStatus,
    data && data.visit_status,
    data && data.state,
    json && json.status
  ));
  const endTime = firstFilled(
    data && data.endTime,
    data && data.end_time,
    data && data.endedAt,
    data && data.ended_at,
    json && json.endTime,
    json && json.end_time
  );
  return status === 'COMPLETED' || !!endTime || isBusinessOk(json);
}

function normalizeStatus(value) {
  const text = String(value || '').trim().toUpperCase();
  if (['COMPLETED', 'ENDED', 'FINISHED', 'DONE'].includes(text)) return 'COMPLETED';
  if (['ONGOING', 'ACTIVE', 'VISITING', 'IN_PROGRESS', 'RUNNING', 'STARTED'].includes(text)) return 'ONGOING';
  return text;
}

function businessMessage(json) {
  if (!json || typeof json !== 'object') return '';
  return firstFilled(json.message, json.msg, json.error, json.status, pretty(json));
}

function unwrapData(json) {
  if (!json || typeof json !== 'object') return json;
  if (Object.prototype.hasOwnProperty.call(json, 'data')) return json.data;
  return json;
}

function extractVisitId(json) {
  const data = unwrapData(json);
  return normalizeId(firstFilled(
    data && data.visitId,
    data && data.visit_id,
    data && data.reportVisitId,
    data && data.report_visit_id,
    json && json.visitId,
    json && json.visit_id
  ));
}

function hasRoutePayload(value) {
  if (!value || typeof value !== 'object') return false;
  const routeKeys = new Set([
    'route',
    'routePlan',
    'route_plan',
    'routeRecommendation',
    'route_recommendation',
    'recommendedSpots',
    'recommended_spots'
  ]);
  const stack = [value];
  while (stack.length > 0) {
    const current = stack.pop();
    if (!current || typeof current !== 'object') continue;
    if (Array.isArray(current)) {
      for (const item of current) stack.push(item);
      continue;
    }
    for (const [key, child] of Object.entries(current)) {
      if (routeKeys.has(key) && isMeaningfulRouteValue(child)) return true;
      if (child && typeof child === 'object') stack.push(child);
    }
  }
  return false;
}

function isMeaningfulRouteValue(value) {
  if (value === null || value === undefined || value === false) return false;
  if (Array.isArray(value)) return value.length > 0;
  if (typeof value === 'object') return Object.keys(value).length > 0;
  if (typeof value === 'string') return value.trim().length > 0 && value.trim().toLowerCase() !== 'false';
  return true;
}

function findFirstDeep(value, keys) {
  const wanted = new Set(keys);
  const stack = [value];
  while (stack.length > 0) {
    const current = stack.pop();
    if (!current || typeof current !== 'object') continue;
    if (Array.isArray(current)) {
      for (const item of current) stack.push(item);
      continue;
    }
    for (const [key, child] of Object.entries(current)) {
      if (wanted.has(key) && child !== undefined && child !== null && child !== '') {
        return String(child);
      }
      if (child && typeof child === 'object') stack.push(child);
    }
  }
  return '';
}

function readBool(object, keys) {
  if (!object || typeof object !== 'object') return undefined;
  for (const key of keys) {
    if (!Object.prototype.hasOwnProperty.call(object, key)) continue;
    const value = object[key];
    if (typeof value === 'boolean') return value;
    if (typeof value === 'number') return value !== 0;
    const text = String(value || '').trim().toLowerCase();
    if (['true', '1', 'yes', 'y'].includes(text)) return true;
    if (['false', '0', 'no', 'n'].includes(text)) return false;
  }
  return undefined;
}

function compactObject(object) {
  const result = {};
  for (const [key, value] of Object.entries(object || {})) {
    if (value === undefined || value === null || value === '') continue;
    if (typeof value === 'object' && !Array.isArray(value)) {
      result[key] = compactObject(value);
    } else {
      result[key] = value;
    }
  }
  return result;
}

function parseJson(text) {
  if (!text) return null;
  try {
    return JSON.parse(text);
  } catch (_) {
    return null;
  }
}

function parseJdbcMysqlUrl(url) {
  const text = String(url || '').trim();
  const match = /^jdbc:mysql:\/\/([^:/?#]+)(?::(\d+))?\/([^?#]+)/i.exec(text);
  if (!match) return {};
  return {
    host: match[1],
    port: match[2] || '3306',
    database: decodeURIComponent(match[3])
  };
}

function asNumberOrString(value) {
  const text = String(value || '').trim();
  if (!text) return '';
  return /^\d+$/.test(text) ? Number(text) : text;
}

function sqlNumber(value) {
  const text = normalizeId(value);
  if (!/^\d+$/.test(text)) {
    throw new Error(`visitId must be numeric for SQL checks: ${value}`);
  }
  return text;
}

function escapeSqlString(value) {
  return String(value || '').replace(/\\/g, '\\\\').replace(/'/g, "\\'");
}

function oneLineSql(sql) {
  return String(sql || '').replace(/\s+/g, ' ').trim();
}

function normalizeId(value) {
  const text = String(value === undefined || value === null ? '' : value).trim();
  return text;
}

function trimTrailingSlash(value) {
  return String(value || '').replace(/\/+$/, '');
}

function firstFilled(...values) {
  for (const value of values) {
    if (value !== undefined && value !== null && String(value).trim() !== '') {
      return String(value).trim();
    }
  }
  return '';
}

function isTruthy(value) {
  if (value === true || value === 1) return true;
  const text = String(value || '').trim().toLowerCase();
  return ['true', '1', 'yes', 'y'].includes(text);
}

function matchValue(text, regex) {
  const match = regex.exec(text || '');
  return match ? match[1].trim() : '';
}

function stripQuotes(value) {
  const text = String(value || '').trim();
  if ((text.startsWith('"') && text.endsWith('"')) || (text.startsWith("'") && text.endsWith("'"))) {
    return text.slice(1, -1);
  }
  return text;
}

function pretty(value) {
  return JSON.stringify(value, null, 2);
}
