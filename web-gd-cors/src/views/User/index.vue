<script setup>
defineOptions({ name: "User" });

function dayin() {
  console.log("hello, world");
  console.log(this);
}

const debounce = (func, delay) => {
  let timer;
  return function (...args) {
    if (timer) clearTimeout(timer);
    timer = setTimeout(() => {
      func.apply(this, args);
    }, delay);
  };
};

let test = debounce(dayin, 1000);
</script>

<template>
  <div
    style="
      align-items: center;
      display: flex;
      justify-content: center;
      height: 100%;
    "
  >
    <el-button type="primary" size="large" @click="test"> 测试 </el-button>
  </div>
</template>

<style scoped lang="scss"></style>
