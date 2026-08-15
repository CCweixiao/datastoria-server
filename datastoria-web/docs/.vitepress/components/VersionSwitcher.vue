<template>
  <div class="version-switcher" ref="root">
    <button
      type="button"
      class="version-button"
      :aria-label="isZh ? '切换文档版本' : 'Switch documentation version'"
      @click="open = !open"
    >
      <span class="version-label">{{ currentLabel }}</span>
      <svg class="chevron" viewBox="0 0 24 24" aria-hidden="true">
        <path d="M6 9l6 6 6-6" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round" />
      </svg>
    </button>
    <ul v-if="open" class="version-menu" role="menu">
      <li v-for="entry in entries" :key="entry.version" role="none">
        <a
          role="menuitem"
          :href="hrefFor(entry)"
          :class="{ active: entry.version === current }"
          @click="open = false"
        >
          <span>{{ labelFor(entry) }}</span>
          <span v-if="entry.version === 'latest'" class="tag">{{ isZh ? '最新' : 'Latest' }}</span>
        </a>
      </li>
    </ul>
  </div>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useData, withBase } from 'vitepress'

const { theme, page, localeIndex } = useData()
const open = ref(false)
const root = ref<HTMLElement | null>(null)

const isZh = computed(() => localeIndex.value !== 'en')

const entries = computed<Array<{ version: string; date?: string | null }>>(
  () => theme.value.versions?.entries ?? [{ version: 'latest' }]
)

// Current version derived from the page path: /{zh/}?{vX.Y.Z/}rest
const current = computed(() => {
  const m = /^\/(?:zh\/)?(v\d+\.\d+\.\d+)\//.exec(page.value.path)
  return m ? m[1] : 'latest'
})

const currentLabel = computed(() =>
  current.value === 'latest' ? (isZh.value ? '最新版' : 'Latest') : current.value
)

function labelFor(entry: { version: string }) {
  if (entry.version === 'latest') return isZh.value ? '最新版本' : 'Latest version'
  return entry.version
}

// Keeps the locale and the current page path while swapping the version segment.
// Chinese pages live at the root; English pages carry the /en prefix.
function hrefFor(entry: { version: string }) {
  let rest = page.value.path.replace(/^\/en\//, '/')
  rest = rest.replace(/^\/v\d+\.\d+\.\d+\//, '/')
  const localePart = isZh.value ? '' : '/en'
  const versionPart = entry.version === 'latest' ? '' : `/${entry.version}`
  return withBase(`${localePart}${versionPart}${rest === '/' ? '/' : rest}`)
}

function onClickOutside(e: MouseEvent) {
  if (root.value && !root.value.contains(e.target as Node)) open.value = false
}

function onKeydown(e: KeyboardEvent) {
  if (e.key === 'Escape') open.value = false
}

onMounted(() => {
  document.addEventListener('click', onClickOutside)
  document.addEventListener('keydown', onKeydown)
})

onBeforeUnmount(() => {
  document.removeEventListener('click', onClickOutside)
  document.removeEventListener('keydown', onKeydown)
})
</script>

<style scoped>
.version-switcher {
  position: relative;
  display: flex;
  align-items: center;
}

.version-button {
  display: flex;
  align-items: center;
  gap: 4px;
  padding: 0 8px;
  height: 36px;
  font-size: 14px;
  font-weight: 500;
  color: var(--vp-c-text-1);
  background: transparent;
  border: none;
  border-radius: 8px;
  cursor: pointer;
}

.version-button:hover {
  color: var(--vp-c-brand-1);
  background-color: var(--vp-c-default-soft);
}

.chevron {
  width: 14px;
  height: 14px;
  opacity: 0.6;
}

.version-menu {
  position: absolute;
  top: 100%;
  right: 0;
  z-index: 100;
  min-width: 160px;
  margin: 0;
  padding: 6px;
  list-style: none;
  background: var(--vp-c-bg);
  border: 1px solid var(--vp-c-divider);
  border-radius: 10px;
  box-shadow: var(--vp-shadow-3);
}

.version-menu a {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 6px 10px;
  font-size: 14px;
  font-weight: 500;
  color: var(--vp-c-text-1);
  border-radius: 6px;
  white-space: nowrap;
}

.version-menu a:hover {
  color: var(--vp-c-brand-1);
  background-color: var(--vp-c-default-soft);
}

.version-menu a.active {
  color: var(--vp-c-brand-1);
}

.tag {
  font-size: 11px;
  line-height: 1;
  padding: 3px 6px;
  border-radius: 999px;
  color: var(--vp-c-brand-1);
  background-color: var(--vp-c-brand-soft);
}
</style>
