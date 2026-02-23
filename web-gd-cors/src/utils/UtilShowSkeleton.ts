import { ref } from "vue";

export function useSkeleton(delay = 300) {
  const skeletonRef = ref<boolean>(false);

  async function runWithSkeleton<T>(promise: Promise<T>): Promise<T> {
    skeletonRef.value = false;

    const delayPromise = new Promise((resolve) => setTimeout(resolve, delay));
    const winner = await Promise.race([promise, delayPromise]);

    if (winner === undefined) skeletonRef.value = true;

    const result = await promise;
    skeletonRef.value = false;

    return result;
  }

  return {
    skeletonRef,
    runWithSkeleton,
  };
}

// 使用方法：
// const { skeletonRef: showSkeleton, runWithSkeleton } = useSkeleton();
// await runWithSkeleton(loadChat(newId));
