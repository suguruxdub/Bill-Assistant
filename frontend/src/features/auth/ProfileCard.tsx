import type { UserProfile } from './types'
import './profile.css'

export default function ProfileCard({ user }: { user: UserProfile }) {
  return (
    <section className="profile-card" aria-label="个人信息">
        <h2>个人信息</h2>
        <dl className="profile-grid">
          <div><dt>用户名</dt><dd>{user.username}</dd></div><div><dt>昵称</dt><dd>{user.nickname}</dd></div>
          <div><dt>邮箱</dt><dd>{user.email || '未设置'}</dd></div><div><dt>联系电话</dt><dd>{user.phone || '未设置'}</dd></div>
          <div><dt>性别</dt><dd>{{ UNSPECIFIED: '未设置', MALE: '男', FEMALE: '女', OTHER: '其他' }[user.gender]}</dd></div><div><dt>生日</dt><dd>{user.birthday || '未设置'}</dd></div>
        </dl>
      </section>
  )
}
