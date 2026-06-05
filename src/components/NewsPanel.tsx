import { useState, useEffect } from 'react'
import { BACKEND_URLS, FALLBACK_NEWS, type NewsItem } from '../config'
import './NewsPanel.css'

export default function NewsPanel() {
  const [news, setNews] = useState<NewsItem[]>(FALLBACK_NEWS)
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    fetch(BACKEND_URLS.news)
      .then((r) => r.json())
      .then((data: NewsItem[]) => {
        if (Array.isArray(data) && data.length > 0) setNews(data)
      })
      .catch(() => {})
      .finally(() => setLoading(false))
  }, [])

  return (
    <aside className="news-panel">
      {loading && <div className="news-loading">Loading news...</div>}
      {news.map((item) => (
        <div
          key={item.id}
          className="news-card"
          style={{ background: item.gradient }}
        >
          <div className="news-emoji">{item.emoji}</div>
          <div className="news-content">
            <span className="news-tag" style={{ color: item.accentColor }}>
              {item.tag}
            </span>
            <span className="news-title">{item.title}</span>
            {item.subtitle && <span className="news-subtitle">{item.subtitle}</span>}
          </div>
          <div className="news-accent-line" style={{ background: item.accentColor }} />
        </div>
      ))}
    </aside>
  )
}
