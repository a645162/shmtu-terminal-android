import { defineConfig } from 'vitepress'

function resolveBase() {
  const repo = process.env.GITHUB_REPOSITORY?.split('/')[1]
  if (!process.env.GITHUB_ACTIONS || !repo) {
    return '/'
  }
  return repo.endsWith('.github.io') ? '/' : `/${repo}/`
}

export default defineConfig({
  base: resolveBase(),
  lang: 'zh-CN',
  title: 'SHMTU Terminal Android 文档',
  description: '上海海事大学校园终端 Android 版使用文档',
  cleanUrls: true,
  lastUpdated: true,
  themeConfig: {
    nav: [
      { text: '快速开始', link: '/guide/quick-start' },
      { text: '功能介绍', link: '/guide/features' },
      { text: 'FAQ', link: '/guide/faq' },
    ],
    sidebar: [
      {
        text: '使用指南',
        items: [
          { text: '文档首页', link: '/' },
          { text: '快速开始', link: '/guide/quick-start' },
          { text: '功能介绍', link: '/guide/features' },
          { text: '账单同步', link: '/guide/bill-sync' },
          { text: 'OCR验证码', link: '/guide/ocr-captcha' },
          { text: '热水分查询', link: '/guide/hot-water' },
          { text: 'FAQ', link: '/guide/faq' },
        ],
      },
    ],
    outline: [2, 3],
    search: {
      provider: 'local',
    },
    footer: {
      message: 'SHMTU Terminal Android Docs',
      copyright: 'Copyright © SHMTU Terminal',
    },
  },
})
