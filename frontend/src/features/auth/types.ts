export type UserProfile = {
  id: number
  username: string
  nickname: string
  email: string | null
  phone: string | null
  avatarUrl: string | null
  gender: 'UNSPECIFIED' | 'MALE' | 'FEMALE' | 'OTHER'
  birthday: string | null
  createdAt: string
  updatedAt: string
}

export type RegisterRequest = {
  username: string
  password: string
  nickname: string | null
  email: string | null
  phone: string | null
  gender: UserProfile['gender']
  birthday: string | null
}

