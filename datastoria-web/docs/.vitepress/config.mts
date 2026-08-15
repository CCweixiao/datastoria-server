import { defineConfig } from 'vitepress'
import { manualSidebar, apiSidebar } from './sidebar-data.mjs'
import { listVersions, loadManifest, localePrefix, rewriteDest, srcToUrl } from './versions.mjs'

const docsBase = process.env.DOCS_BASE || '/'
const docsOrigin = process.env.DOCS_ORIGIN || 'https://ccweixiao.github.io'
const docsAsset = (path: string) => `${docsBase}${path.replace(/^\/+/, '')}`
const docsUrl = (path: string) => `${docsOrigin}${docsAsset(path)}`

// ---------------------------------------------------------------------------
// Versions: snapshots under docs/versions/<vX.Y.Z>/ are discovered at build
// time. The latest content lives at the site root and has no version segment.
// ---------------------------------------------------------------------------
const versions = listVersions()
const versionEntries = versions.map((v) => ({
  version: v,
  date: loadManifest(v)?.date ?? null,
}))

/** Latest-version flag for the version switcher UI. */
const LATEST = 'latest'

/**
 * Builds the full sidebar map for one locale across all versions. Versioned
 * prefixes use the sidebar frozen in that version's manifest so later content
 * changes never leak into old snapshots.
 */
function buildSidebar(lang: 'en' | 'zh') {
  const sidebar: Record<string, any> = {}
  const applyPrefix = (groups: any[], prefix: string) =>
    JSON.parse(JSON.stringify(groups)).map((group: any) => {
      const fix = (item: any) => {
        if (item.link !== undefined) item.link = item.link === '' ? prefix.slice(0, -1) || '/' : `${prefix}${item.link}`
        if (item.items) item.items = item.items.map(fix)
        return item
      }
      return fix(group)
    })

  // Latest version (site root, no version segment)
  const enPrefix = localePrefix('en')
  const zhPrefix = localePrefix('zh')
  const rootPrefix = lang === 'zh' ? zhPrefix : enPrefix
  sidebar[`${rootPrefix}manual/`] = applyPrefix(manualSidebar(lang), `${rootPrefix}manual/`)
  sidebar[`${rootPrefix}reference/api/`] = applyPrefix(apiSidebar(lang), `${rootPrefix}reference/api/`)

  // Versioned snapshots
  for (const version of versions) {
    const manifest = loadManifest(version)
    const groups =
      manifest?.sidebar?.[lang] ??
      // Manifests written before this locale existed fall back to current data.
      manualSidebar(lang)
    const prefix = localePrefix(lang, version)
    sidebar[`${prefix}manual/`] = applyPrefix(groups, `${prefix}manual/`)
    sidebar[`${prefix}reference/api/`] = applyPrefix(apiSidebar(lang), `${prefix}reference/api/`)
  }
  return sidebar
}

function buildNav(lang: 'en' | 'zh') {
  const prefix = localePrefix(lang)
  return [
    { text: lang === 'zh' ? '首页' : 'Home', link: prefix || '/' },
    { text: lang === 'zh' ? '使用手册' : 'Manual', link: `${prefix}manual/` },
    { text: 'API', link: `${prefix}reference/api/` },
    { component: 'VersionSwitcher' },
  ]
}

/** Computes the base-less site path of a page from its source relativePath. */
function sitePath(relativePath: string): string {
  const rewritten = srcToUrl(relativePath)
  if (rewritten !== undefined) return `/${rewritten}`
  return `/${relativePath.replace(/(?:^|\/)index\.md$/, '').replace(/\.md$/, '')}`
}

export default defineConfig({
  title: 'DataStoria Documentation',
  description: 'AI-powered ClickHouse management console with natural language queries, intelligent optimization, and advanced cluster management. Modern web interface for ClickHouse database administration.',
  base: docsBase,
  ignoreDeadLinks: false,

  // SEO: Clean URLs without .html extension
  cleanUrls: true,

  // Rewrites: docs/versions/<v>/** → /<v>/**, docs/versions/<v>/zh/** → /zh/<v>/**
  rewrites: (id) => rewriteDest(id) as any,

  locales: {
    root: {
      label: 'English',
      lang: 'en-US',
      themeConfig: {
        nav: buildNav('en'),
        sidebar: buildSidebar('en'),
        outline: {
          level: [2, 3],
          label: 'On this page',
        },
        footer: {
          message: 'Released under the Apache License 2.0',
          copyright: 'Copyright © 2025 DataStoria',
        },
      },
    },
    zh: {
      label: '简体中文',
      lang: 'zh-CN',
      title: 'DataStoria 文档',
      description: 'AI 驱动的 ClickHouse 管理控制台：自然语言查询、智能优化与集群管理，现代化的 ClickHouse 数据库管理 Web 界面。',
      themeConfig: {
        nav: buildNav('zh'),
        sidebar: buildSidebar('zh'),
        outline: {
          level: [2, 3],
          label: '本页内容',
        },
        footer: {
          message: '基于 Apache License 2.0 发布',
          copyright: 'Copyright © 2025 DataStoria',
        },
      },
    },
  },

  // SEO: Global meta tags
  head: [
    // Basic meta tags
    ['meta', { name: 'viewport', content: 'width=device-width, initial-scale=1.0' }],
    ['meta', { name: 'theme-color', content: '#3b82f6' }],
    ['meta', { charset: 'utf-8' }],

    // SEO meta tags
    ['meta', { name: 'keywords', content: 'ClickHouse, database management, AI SQL, natural language query, ClickHouse console, database admin, query optimization, ClickHouse GUI, ClickHouse web interface, SQL editor' }],
    ['meta', { name: 'author', content: 'DataStoria' }],
    ['meta', { name: 'robots', content: 'index, follow, max-image-preview:large, max-snippet:-1, max-video-preview:-1' }],

    // Favicon and app icons
    ['link', { rel: 'icon', href: docsAsset('/favicon.ico'), sizes: 'any' }],
    ['link', { rel: 'icon', href: docsAsset('/icon.svg'), type: 'image/svg+xml' }],
    ['link', { rel: 'apple-touch-icon', href: docsAsset('/apple-touch-icon.png') }],
    ['link', { rel: 'manifest', href: docsAsset('/site.webmanifest') }],

    // Open Graph tags for social sharing (Facebook, LinkedIn, etc.)
    ['meta', { property: 'og:type', content: 'website' }],
    ['meta', { property: 'og:site_name', content: 'DataStoria Documentation' }],
    ['meta', { property: 'og:title', content: 'DataStoria - AI-Powered ClickHouse Management Console' }],
    ['meta', { property: 'og:description', content: 'Modern ClickHouse management console with AI-powered natural language queries, intelligent optimization, and advanced cluster management capabilities.' }],
    ['meta', { property: 'og:image', content: docsUrl('/logo.png') }],
    ['meta', { property: 'og:image:width', content: '1200' }],
    ['meta', { property: 'og:image:height', content: '630' }],
    ['meta', { property: 'og:image:alt', content: 'DataStoria - AI-Powered ClickHouse Management Console' }],
    ['meta', { property: 'og:locale', content: 'en_US' }],

    // Twitter Card tags
    ['meta', { name: 'twitter:card', content: 'summary_large_image' }],
    ['meta', { name: 'twitter:site', content: '@datastoria' }],
    ['meta', { name: 'twitter:title', content: 'DataStoria - AI-Powered ClickHouse Management Console' }],
    ['meta', { name: 'twitter:description', content: 'Modern ClickHouse management console with AI-powered natural language queries, intelligent optimization, and advanced cluster management.' }],
    ['meta', { name: 'twitter:image', content: docsUrl('/logo.png') }],
    ['meta', { name: 'twitter:image:alt', content: 'DataStoria - AI-Powered ClickHouse Management Console' }],

    // Additional SEO enhancements
    ['meta', { name: 'format-detection', content: 'telephone=no' }],
    ['meta', { name: 'application-name', content: 'DataStoria' }],
    ['meta', { name: 'mobile-web-app-capable', content: 'yes' }],
    ['meta', { name: 'apple-mobile-web-app-capable', content: 'yes' }], // Keep for iOS compatibility
    ['meta', { name: 'apple-mobile-web-app-status-bar-style', content: 'black-translucent' }],
    ['meta', { name: 'apple-mobile-web-app-title', content: 'DataStoria' }],

    // Structured Data (JSON-LD) for rich search results
    ['script', { type: 'application/ld+json' }, JSON.stringify({
      '@context': 'https://schema.org',
      '@type': 'SoftwareApplication',
      name: 'DataStoria',
      applicationCategory: 'DatabaseApplication',
      operatingSystem: 'Web Browser',
      description: 'AI-powered ClickHouse management console with natural language queries, intelligent optimization, and advanced cluster management capabilities.',
      url: 'https://github.com/CCweixiao/datastoria-server',
      image: docsUrl('/logo.png'),
      author: {
        '@type': 'Organization',
        name: 'DataStoria',
        url: 'https://github.com/CCweixiao/datastoria-server'
      },
      offers: {
        '@type': 'Offer',
        price: '0',
        priceCurrency: 'USD'
      },
      aggregateRating: {
        '@type': 'AggregateRating',
        ratingValue: '5',
        ratingCount: '1'
      },
      softwareVersion: '1.0',
      releaseNotes: 'https://github.com/CCweixiao/datastoria-server/tree/master/docs/manual/',
      screenshot: docsUrl('/logo.png'),
      featureList: [
        'Natural Language to SQL conversion',
        'AI-powered query optimization',
        'Intelligent data visualization',
        'Multi-cluster management',
        'Real-time performance monitoring',
        'Advanced SQL editor with syntax highlighting',
        'Query explain visualization',
        'System log introspection',
        'Privacy-first architecture'
      ]
    })],

    // Breadcrumb structured data
    ['script', { type: 'application/ld+json' }, JSON.stringify({
      '@context': 'https://schema.org',
      '@type': 'BreadcrumbList',
      itemListElement: [
        {
          '@type': 'ListItem',
          position: 1,
          name: 'Home',
          item: docsUrl('/')
        },
        {
          '@type': 'ListItem',
          position: 2,
          name: 'Documentation',
          item: docsUrl('/manual/')
        }
      ]
    })],

    // Organization structured data
    ['script', { type: 'application/ld+json' }, JSON.stringify({
      '@context': 'https://schema.org',
      '@type': 'Organization',
      name: 'DataStoria',
      url: 'https://github.com/CCweixiao/datastoria-server',
      logo: docsUrl('/logo.png'),
      sameAs: [
        'https://github.com/CCweixiao/datastoria-server'
      ],
      contactPoint: {
        '@type': 'ContactPoint',
        contactType: 'Customer Support',
        url: 'https://github.com/CCweixiao/datastoria-server/issues'
      }
    })],

    // WebSite structured data
    ['script', { type: 'application/ld+json' }, JSON.stringify({
      '@context': 'https://schema.org',
      '@type': 'WebSite',
      name: 'DataStoria Documentation',
      url: docsUrl('/')
    })],

    // Preconnect to CDN for faster resource loading
    ['link', { rel: 'preconnect', href: 'https://cdn.jsdelivr.net', crossorigin: '' }],
    ['link', { rel: 'dns-prefetch', href: 'https://cdn.jsdelivr.net' }],

    // Mermaid for diagrams - Load asynchronously to avoid render blocking
    ['script', {
      src: 'https://cdn.jsdelivr.net/npm/mermaid@10/dist/mermaid.min.js',
      async: '',
      defer: ''
    }],
    ['script', {}, `
      (function() {
        let mermaidInitialized = false;
        let mermaidLoaded = false;

        // Lazy load Mermaid only when needed (when mermaid diagrams are present)
        function checkAndLoadMermaid() {
          const hasMermaid = document.querySelector('.mermaid');
          if (hasMermaid && !mermaidLoaded) {
            mermaidLoaded = true;
            initMermaid();
          }
        }

        function initMermaid() {
          if (typeof window.mermaid === 'undefined') {
            // Wait for script to load
            setTimeout(initMermaid, 50);
            return;
          }

          if (!mermaidInitialized) {
            window.mermaid.initialize({
              startOnLoad: false,
              theme: 'default',
              securityLevel: 'loose'
            });
            mermaidInitialized = true;
          }

          // Render all mermaid diagrams
          renderMermaidDiagrams();
        }

        function renderMermaidDiagrams() {
          if (typeof window.mermaid === 'undefined' || !mermaidInitialized) {
            return;
          }

          const mermaidElements = document.querySelectorAll('.mermaid:not([data-processed])');
          if (mermaidElements.length === 0) return;

          mermaidElements.forEach((element, index) => {
            const id = 'mermaid-' + Date.now() + '-' + index + '-' + Math.random().toString(36).substr(2, 9);
            // Get the text content and unescape HTML entities
            let code = (element.textContent || element.innerText || '').trim();
            code = code
              .replace(/&amp;/g, '&')
              .replace(/&lt;/g, '<')
              .replace(/&gt;/g, '>')
              .replace(/&quot;/g, '"')
              .replace(/&#39;/g, "'");

            if (code) {
              element.setAttribute('data-processed', 'true');

              try {
                // Use the async render API
                window.mermaid.render(id, code).then((result) => {
                  element.innerHTML = result.svg;
                }).catch((error) => {
                  console.error('Mermaid render error:', error);
                  element.innerHTML = '<pre style="color: red;">Error rendering diagram:\\n' + code + '</pre>';
                });
              } catch (error) {
                // Fallback for older API
                try {
                  window.mermaid.render(id, code, (svgCode) => {
                    element.innerHTML = svgCode;
                  });
                } catch (e) {
                  console.error('Mermaid render error:', e);
                  element.innerHTML = '<pre style="color: red;">Error rendering diagram:\\n' + code + '</pre>';
                }
              }
            }
          });
        }

        // Use Intersection Observer for lazy loading (better performance)
        if ('IntersectionObserver' in window) {
          const observer = new IntersectionObserver((entries) => {
            entries.forEach((entry) => {
              if (entry.isIntersecting && entry.target.classList.contains('mermaid')) {
                checkAndLoadMermaid();
                observer.unobserve(entry.target);
              }
            });
          }, { rootMargin: '50px' });

          // Observe mermaid elements when DOM is ready
          function observeMermaidElements() {
            document.querySelectorAll('.mermaid:not([data-processed])').forEach((el) => {
              observer.observe(el);
            });
          }

          if (document.readyState === 'loading') {
            document.addEventListener('DOMContentLoaded', observeMermaidElements);
          } else {
            observeMermaidElements();
          }

          // Re-observe on route changes (VitePress SPA navigation)
          if (typeof window !== 'undefined') {
            const mutationObserver = new MutationObserver(() => {
              observeMermaidElements();
            });

            setTimeout(() => {
              if (document.body) {
                mutationObserver.observe(document.body, {
                  childList: true,
                  subtree: true
                });
              }
            }, 100);
          }
        } else {
          // Fallback for browsers without IntersectionObserver
          if (document.readyState === 'loading') {
            document.addEventListener('DOMContentLoaded', checkAndLoadMermaid);
          } else {
            checkAndLoadMermaid();
          }
        }
      })();
    `],
  ],

  // SEO: Automatic sitemap generation
  sitemap: {
    hostname: docsUrl('/'),
    lastmodDateOnly: false, // Include time in lastmod
    transformItems: (items) => {
      const versionRoots = new Set<string>()
      const versionPrefixes: string[] = []
      for (const v of versions) {
        versionRoots.add(`/${v}/`)
        versionRoots.add(`/zh/${v}/`)
        versionPrefixes.push(`/${v}/`)
      }
      return items.map((item) => {
        // Homepages get the highest priority
        if (item.url === '/' || item.url === '/zh/' || versionRoots.has(item.url)) {
          return { ...item, priority: 1.0, changefreq: 'weekly' }
        }
        // Versioned snapshots are frozen content
        if (versionPrefixes.some((p) => item.url.startsWith(p) || item.url.startsWith(`/zh${p}`))) {
          return { ...item, priority: 0.4, changefreq: 'yearly' }
        }
        // Manual pages get high priority
        if (item.url.startsWith('/manual/') || item.url.startsWith('/zh/manual/')) {
          return { ...item, priority: 0.8, changefreq: 'weekly' }
        }
        // Other pages get standard priority
        return { ...item, priority: 0.5, changefreq: 'monthly' }
      })
    }
  },

  // SEO: Last updated dates (helps search engines understand content freshness)
  lastUpdated: true,

  // SEO: Generate meta tags for each page
  transformPageData(pageData) {
    const path = sitePath(pageData.relativePath)
    const canonicalUrl = docsUrl(path)
    const isZh = path === '/zh/' || path.startsWith('/zh/')
    const titleSuffix = isZh ? 'DataStoria 文档' : 'DataStoria Documentation'

    pageData.frontmatter.head ??= []
    // Ensure only one canonical URL exists per page.
    pageData.frontmatter.head = pageData.frontmatter.head.filter((item) => {
      const [tag, attrs] = item as [string, Record<string, string> | undefined]
      return !(tag === 'link' && attrs?.rel === 'canonical')
    })
    pageData.frontmatter.head.push([
      'link',
      { rel: 'canonical', href: canonicalUrl }
    ])

    // Add Open Graph URL for each page
    pageData.frontmatter.head = pageData.frontmatter.head.filter((item) => {
      const [tag, attrs] = item as [string, Record<string, string> | undefined]
      return !(tag === 'meta' && attrs?.property === 'og:url')
    })
    pageData.frontmatter.head.push([
      'meta',
      { property: 'og:url', content: canonicalUrl }
    ])

    // Per-locale Open Graph locale, with the other language as alternate.
    if (isZh) {
      pageData.frontmatter.head.push(['meta', { property: 'og:locale', content: 'zh_CN' }])
      pageData.frontmatter.head.push(['meta', { property: 'og:locale:alternate', content: 'en_US' }])
    }

    // hreflang alternates for pages that exist in both languages (dev/ notes are
    // English-only). Path shape: /{zh/}?{vX/}?rest → sibling in the other locale.
    const localeless = path.replace(/^\/zh(?=\/)/, '')
    const isTranslated = localeless === '/' || /^\/(v[\d.]+\/)?(manual|reference)\//.test(localeless)
    if (isTranslated) {
      const enUrl = docsUrl(localeless)
      const zhUrl = docsUrl(`/zh${localeless === '/' ? '/' : localeless}`)
      pageData.frontmatter.head.push(['link', { rel: 'alternate', hreflang: 'en', href: enUrl }])
      pageData.frontmatter.head.push(['link', { rel: 'alternate', hreflang: 'zh', href: zhUrl }])
      pageData.frontmatter.head.push(['link', { rel: 'alternate', hreflang: 'x-default', href: enUrl }])
    }

    // Add page-specific title and description to Open Graph
    if (pageData.title) {
      pageData.frontmatter.head.push([
        'meta',
        { property: 'og:title', content: `${pageData.title} | ${titleSuffix}` }
      ])
      pageData.frontmatter.head.push([
        'meta',
        { name: 'twitter:title', content: `${pageData.title} | ${titleSuffix}` }
      ])
    }

    if (pageData.description) {
      pageData.frontmatter.head.push([
        'meta',
        { property: 'og:description', content: pageData.description }
      ])
      pageData.frontmatter.head.push([
        'meta',
        { name: 'twitter:description', content: pageData.description }
      ])
    }
  },

  // SEO: Generate title template for better page titles
  titleTemplate: ':title | DataStoria Documentation',

  // Markdown configuration for Mermaid
  markdown: {
    config: (md) => {
      // Custom plugin to handle mermaid code blocks
      const defaultFence = md.renderer.rules.fence
      if (defaultFence) {
        md.renderer.rules.fence = (tokens, idx, options, env, self) => {
          const token = tokens[idx]
          const info = token.info ? token.info.trim() : ''
          if (info === 'mermaid') {
            // Escape HTML entities in the content to prevent rendering issues
            const content = token.content
              .replace(/&/g, '&amp;')
              .replace(/</g, '&lt;')
              .replace(/>/g, '&gt;')
              .replace(/"/g, '&quot;')
              .replace(/'/g, '&#39;')
            // Return a pre tag with mermaid class - the script will process it
            return `<pre class="mermaid">${content}</pre>`
          }
          return defaultFence(tokens, idx, options, env, self)
        }
      }
    }
  },

  themeConfig: {
    logo: '/logo.png', // VitePress applies base to theme asset paths.

    // Version metadata consumed by the VersionSwitcher nav component.
    versions: {
      latest: LATEST,
      entries: [
        { version: LATEST },
        ...versionEntries,
      ],
    },

    socialLinks: [
      { icon: 'github', link: 'https://github.com/CCweixiao/datastoria-server' }
    ],

    search: {
      provider: 'local',
      options: {
        // Default miniSearch tokenization treats a CJK sentence as one token,
        // which makes Chinese content unsearchable. Use word + CJK n-gram.
        miniSearch: {
          options: {
            tokenize: (text: string) => {
              const tokens: string[] = []
              for (const match of text.matchAll(/[A-Za-z0-9_]+/g)) {
                tokens.push(match[0].toLowerCase())
              }
              const cjkRuns = text.match(/[㐀-鿿豈-﫿]+/g) ?? []
              for (const run of cjkRuns) {
                for (let i = 0; i < run.length; i++) {
                  tokens.push(run[i])
                  if (i + 1 < run.length) tokens.push(run[i] + run[i + 1])
                }
              }
              return tokens
            },
          },
        },
      },
      locales: {
        zh: {
          translations: {
            button: { buttonText: '搜索文档', buttonAriaLabel: '搜索文档' },
            modal: {
              noResultsText: '未找到相关结果',
              resetButtonTitle: '清除查询',
              footer: { selectText: '选择', navigateText: '切换', closeText: '关闭' },
            },
          },
        },
      },
    },

    // Right sidebar: Table of Contents (TOC) / Outline
    // Automatically generated from h2, h3, etc. in your markdown
    // (label moved into per-locale themeConfig)

    footer: {
      message: 'Released under the Apache License 2.0',
      copyright: 'Copyright © 2025 DataStoria'
    },
  },

  // Performance optimizations
  vite: {
    build: {
      // Enable minification with esbuild (default, faster than terser)
      minify: 'esbuild',
      // Enable CSS code splitting
      cssCodeSplit: true,
      // Increase chunk size warning limit
      chunkSizeWarningLimit: 1000,
    },
    // Enable CSS preprocessing optimizations
    css: {
      devSourcemap: false,
    },
    // Drop console and debugger in production
    esbuild: {
      drop: ['console', 'debugger'],
    },
  },
})
