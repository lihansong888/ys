import requests

def main():
    # --- 1. 初始化 ---
    output = []
    headers = {"User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36"}

    # --- 2. 精品频道 (保留你实测秒开的源) ---
    output.append("精品频道,#genre#")
    try:
        r = requests.get("http://adultiptv.net/chs.m3u", headers=headers, timeout=10)
        if r.status_code == 200:
            name = ""
            for line in r.text.split('\n'):
                if "#EXTINF" in line: name = line.split(',')[-1].strip()
                elif "http" in line and name:
                    output.append(f"{name},{line.strip()}")
                    name = ""
    except: pass

    # --- 3. 核心直播 (直接从你提供的 Gitee IPTV 源提取) ---
    output.append("核心直播,#genre#")
    # 这个源里藏着你截图显示的“中国IPTV”原生流
    iptv_raw_url = "https://gitee.com/yimi321/tv/raw/master/tv.png"
    try:
        res = requests.get(iptv_raw_url, headers=headers, timeout=15)
        if res.status_code == 200:
            # 这种源通常是 txt 格式，我们直接过滤并整合
            for line in res.text.split('\n'):
                line = line.strip()
                # 只抓取真正带 http 的 IPTV 链接
                if "," in line and "http" in line:
                    # 优先提取 CCTV、卫视和咪咕，确保列表精简好用
                    if any(x in line for x in ["CCTV", "卫视", "咪咕"]):
                        output.append(line)
    except Exception as e:
        print(f"提取 IPTV 出错: {e}")

    # --- 4. 生成最终 migu.txt ---
    with open("migu.txt", "w", encoding="utf-8") as f:
        f.write("\n".join(output))
    print("IPTV 核心源已成功提取并整合")

if __name__ == "__main__":
    main()
