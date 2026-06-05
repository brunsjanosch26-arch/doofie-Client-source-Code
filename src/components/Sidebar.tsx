import type { Account } from '../App'
import './Sidebar.css'

export type Page = 'home' | 'mods' | 'shaders' | 'servers' | 'capes' | 'account' | 'settings'

interface SidebarProps {
  currentPage: Page
  onNavigate: (page: Page) => void
  account?: Account | null
}

const NAV_ITEMS: { page: Page; icon: string; label: string }[] = [
  { page: 'home',     icon: '▶',  label: 'Spielen'  },
  { page: 'mods',     icon: '⊞',  label: 'Mods'     },
  { page: 'shaders',  icon: '✦',  label: 'Shader'   },
  { page: 'servers',  icon: '⊟',  label: 'Server'   },
  { page: 'capes',    icon: '◈',  label: 'Capes'    },
  { page: 'account',  icon: '◉',  label: 'Account'  },
]

export default function Sidebar({ currentPage, onNavigate, account }: SidebarProps) {
  return (
    <nav className="sidebar">
      <div className="sidebar-logo" onClick={() => onNavigate('home')} title="Cookie Client">
        ⚡
      </div>

      <div className="sidebar-nav">
        {NAV_ITEMS.map(({ page, icon, label }) => (
          <button
            key={page}
            className={`nav-btn ${currentPage === page ? 'active' : ''}`}
            onClick={() => onNavigate(page)}
            title={label}
          >
            <span className="nav-icon">{icon}</span>
            {currentPage === page && <div className="active-bar" />}
          </button>
        ))}
      </div>

      <div className="sidebar-bottom">
        {account && (
          <button
            className="sidebar-avatar-btn"
            onClick={() => onNavigate('account')}
            title={account.username}
          >
            <img
              src={`https://mc-heads.net/avatar/${account.uuid}/32`}
              alt={account.username}
              className="sidebar-avatar"
              onError={(e) => { (e.target as HTMLImageElement).src = 'https://mc-heads.net/avatar/MHF_Steve/32' }}
            />
          </button>
        )}
        <button
          className={`nav-btn ${currentPage === 'settings' ? 'active' : ''}`}
          onClick={() => onNavigate('settings')}
          title="Settings"
        >
          <span className="nav-icon">⚙</span>
          {currentPage === 'settings' && <div className="active-bar" />}
        </button>
      </div>
    </nav>
  )
}
