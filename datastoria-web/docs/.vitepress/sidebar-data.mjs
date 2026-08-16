// Pure sidebar data shared by .vitepress/config.mts and scripts/docs/snapshot-version.mjs.
// No vitepress imports here — this module must stay loadable from plain Node scripts.

/**
 * @param {'en' | 'zh'} lang
 * @returns {Array<import('vitepress').DefaultTheme.SidebarItem>} manual sidebar groups
 */
export function manualSidebar(lang) {
  const t = {
    gettingStarted: lang === 'zh' ? '快速开始' : 'Getting Started',
    ai: lang === 'zh' ? 'AI 智能功能' : 'AI-Powered Intelligence',
    query: lang === 'zh' ? '查询体验' : 'Query Experience',
    database: lang === 'zh' ? '数据库管理' : 'Database Management',
    dbViews: lang === 'zh' ? '数据库与表视图' : 'Database & Table Views',
    systemLog: lang === 'zh' ? '系统日志内省' : 'System Log Introspection',
    overview: lang === 'zh' ? '概述' : 'Overview',
    monitoring: lang === 'zh' ? '监控与仪表盘' : 'Monitoring & Dashboards',
    security: lang === 'zh' ? '安全与隐私' : 'Security & Privacy',
    admin: lang === 'zh' ? '管理控制台' : 'Admin Console',
    introduction: lang === 'zh' ? '产品介绍' : 'Introduction',
    installation: lang === 'zh' ? '安装与配置' : 'Installation & Setup',
    firstConnection: lang === 'zh' ? '首次连接' : 'First Connection',
    aiModelConfig: lang === 'zh' ? 'AI 模型配置' : 'AI Model Configuration',
    nlsq: lang === 'zh' ? '自然语言数据探索' : 'Natural Language Data Exploration',
    queryOptimization: lang === 'zh' ? '查询优化' : 'Query Optimization',
    visualization: lang === 'zh' ? '智能可视化' : 'Intelligent Visualization',
    askAi: lang === 'zh' ? 'AI 助手求助' : 'Ask AI for Help',
    slashCommands: lang === 'zh' ? '斜杠命令' : 'Slash Commands',
    skills: lang === 'zh' ? 'Agent 技能' : 'Agent Skills',
    agentRuntime: lang === 'zh' ? '智能体运行参数' : 'Agent Runtime Settings',
    healthAdvisor: lang === 'zh' ? '集群健康顾问' : 'Cluster Health Advisor',
    sqlSnippets: lang === 'zh' ? 'SQL 代码片段' : 'SQL Snippets',
    sqlEditor: lang === 'zh' ? 'SQL 编辑器' : 'SQL Editor',
    queryExecution: lang === 'zh' ? '查询执行' : 'Query Execution',
    queryExplain: lang === 'zh' ? '执行计划分析' : 'Query Explain',
    queryLog: lang === 'zh' ? '查询日志检查器' : 'Query Log Inspector',
    errorDiagnostics: lang === 'zh' ? '错误诊断' : 'Error Diagnostics',
    schemaExplorer: lang === 'zh' ? '模式浏览器' : 'Schema Explorer',
    databaseView: lang === 'zh' ? '数据库视图' : 'Database View',
    tableView: lang === 'zh' ? '表视图' : 'Table View',
    dependencyView: lang === 'zh' ? '依赖视图' : 'Dependency View',
    nodeDashboard: lang === 'zh' ? '节点仪表盘' : 'Node Dashboard',
    clusterDashboard: lang === 'zh' ? '集群仪表盘' : 'Cluster Dashboard',
    privacy: lang === 'zh' ? '隐私特性' : 'Privacy Features',
    adminConsole: lang === 'zh' ? '管理平台操作手册' : 'Admin Console Manual',
  }

  return [
    {
      text: t.gettingStarted,
      collapsed: false,
      items: [
        { text: t.introduction, link: '01-getting-started/introduction' },
        { text: t.installation, link: '01-getting-started/installation' },
        { text: t.firstConnection, link: '01-getting-started/first-connection' },
      ],
    },
    {
      text: t.ai,
      collapsed: false,
      items: [
        { text: t.aiModelConfig, link: '02-ai-features/ai-model-configuration' },
        { text: t.nlsq, link: '02-ai-features/natural-language-sql' },
        { text: t.queryOptimization, link: '02-ai-features/query-optimization' },
        { text: t.visualization, link: '02-ai-features/intelligent-visualization' },
        { text: t.askAi, link: '02-ai-features/ask-ai-for-help' },
        { text: t.slashCommands, link: '02-ai-features/slash-commands' },
        { text: t.skills, link: '02-ai-features/skills' },
        { text: t.agentRuntime, link: '02-ai-features/agent-runtime-settings' },
        { text: t.healthAdvisor, link: '02-ai-features/cluster-health-advisor' },
      ],
    },
    {
      text: t.query,
      collapsed: false,
      items: [
        { text: t.sqlEditor, link: '03-query-experience/sql-editor' },
        { text: t.queryExecution, link: '03-query-experience/query-execution' },
        { text: t.queryExplain, link: '03-query-experience/query-explain' },
        { text: t.queryLog, link: '03-query-experience/query-log-inspector' },
        { text: t.sqlSnippets, link: '03-query-experience/sql-snippets' },
        { text: t.errorDiagnostics, link: '03-query-experience/error-diagnostics' },
      ],
    },
    {
      text: t.database,
      collapsed: false,
      items: [
        { text: t.schemaExplorer, link: '04-cluster-management/schema-explorer' },
        {
          text: t.dbViews,
          collapsed: false,
          items: [
            { text: t.databaseView, link: '04-cluster-management/database-view' },
            { text: t.tableView, link: '04-cluster-management/table-view' },
            { text: t.dependencyView, link: '04-cluster-management/dependency-view' },
          ],
        },
        {
          text: t.systemLog,
          collapsed: false,
          items: [
            { text: t.overview, link: '04-cluster-management/system-log-introspection' },
            { text: 'system.ddl_distribution_queue', link: '04-cluster-management/system-ddl-distributed-queue' },
            { text: 'system.opentelemetry_span_log', link: '04-cluster-management/opentelemetry-span-log' },
            { text: 'system.part_log', link: '04-cluster-management/system-part-log' },
            { text: 'system.query_log', link: '04-cluster-management/system-query-log' },
            { text: 'system.query_views_log', link: '04-cluster-management/system-query-views-log' },
            { text: 'system.processes', link: '04-cluster-management/system-processes' },
            { text: 'system.zookeeper', link: '04-cluster-management/system-zookeeper' },
          ],
        },
      ],
    },
    {
      text: t.monitoring,
      collapsed: false,
      items: [
        { text: t.nodeDashboard, link: '05-monitoring-dashboards/node-dashboard' },
        { text: t.clusterDashboard, link: '05-monitoring-dashboards/cluster-dashboard' },
      ],
    },
    {
      text: t.security,
      collapsed: false,
      items: [{ text: t.privacy, link: '06-security-privacy/privacy-features' }],
    },
    {
      text: t.admin,
      collapsed: false,
      items: [{ text: t.adminConsole, link: '07-admin-console/admin-console' }],
    },
  ]
}

/**
 * @param {'en' | 'zh'} lang
 * @returns {Array<import('vitepress').DefaultTheme.SidebarItem>} API reference sidebar
 */
export function apiSidebar(lang) {
  return [
    {
      text: lang === 'zh' ? 'API 参考' : 'API Reference',
      items: [{ text: 'HTTP API', link: '' }],
    },
  ]
}
