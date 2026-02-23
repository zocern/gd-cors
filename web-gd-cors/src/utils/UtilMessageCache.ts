/**
 * 消息缓存类 - 用于管理流式消息的本地缓存
 */

interface CacheData {
  content: string;
  lastChunkId: string | null;
  isWaitingResponse?: boolean;
}

const CACHE_PREFIX = "sessionCache_";

class MessageCache {
  /**
   * 保存缓存
   */
  save(
    sessionId: string, 
    content: string, 
    lastChunkId: string | null,
    isWaitingResponse: boolean = false
  ): void {
    if (!sessionId) return;

    const data: CacheData = {
      content,
      lastChunkId,
      isWaitingResponse,
    };

    try {
      localStorage.setItem(`${CACHE_PREFIX}${sessionId}`, JSON.stringify(data));
    } catch (e) {
      console.warn("[MessageCache] 保存失败", e);
    }
  }

  /**
   * 读取缓存
   */
  get(sessionId: string): CacheData | null {
    if (!sessionId) return null;

    try {
      const raw = localStorage.getItem(`${CACHE_PREFIX}${sessionId}`);
      if (!raw) return null;

      return JSON.parse(raw);
    } catch (e) {
      console.warn("[MessageCache] 读取失败", e);
      return null;
    }
  }

  /**
   * 删除缓存
   */
  remove(sessionId: string): void {
    if (!sessionId) return;
    localStorage.removeItem(`${CACHE_PREFIX}${sessionId}`);
  }

  /**
   * 检查是否存在缓存
   */
  has(sessionId: string): boolean {
    return this.get(sessionId) !== null;
  }
}

export const messageCache = new MessageCache();
export type { CacheData };
