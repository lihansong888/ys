import requests
import re

def main():
    # --- 1. 初始化列表 ---
    final_output = []
    headers = {"User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36"}

    # --- 2. 核心直播：我为你找的最硬核源站 ---
    # 这些是目前全网最顶级的、由大神实时维护的 IPTV 原始单播地址库
    # 包含了你截图里那种能起像的“中国IPTV”原生流
    search_targets = [
        "https://raw.githubusercontent.com/yuanzl77/IPTV/main/live.txt", # 极其稳定的运营商源
        "https://gitee.com/yimi321/tv/raw/master/tv.png",               # 你实测有效的那个源
        "https://raw.githubusercontent.com/ssili126/tv/main/itvlist.txt" # 纯净的 IPTV 组播转单播地址
    ]

    final_output.append("核心直播,#genre#")
    
    # 建立一个去重集合，防止频道重复
    seen_urls = set()
    
    for url in search_targets:
        try:
            res = requests.get(url, headers=headers, timeout=10)
            if res.status_code == 200:
                for line in res.text.split('\n'):
                    line = line.strip()
                    # 严格遵守你的 XBPQ 范本逻辑：频道名,链接
                    if "," in line and "http" in line:
                        name, link = line.split(',', 1)
                        # 过滤核心频道，确保你打开电视就能看最想看的
                        if any(x in name for x in ["CCTV", "卫视", "咪咕"]):
                            if link not in seen_urls:
                                final_output.append(f"{name},{link}")
                                seen_urls.add(link)
            if len(final_output) > 50: break # 抓够精选的就停，保证加载速度
        except:
            continue

    # --- 3. 精品频道 (保留那个一直有效的 adult 源) ---
    final_output.append("精品频道,#genre#")
    try:
        r = requests.get("http://adultiptv.net/chs.m3u", timeout=10)
        if r.status_code == 200:
            name = ""
            for line in r.text.split('\n'):
                if "#EXTINF" in line: name = line.split(',')[-1].strip()
                elif "http" in line and name:
                    final_output.append(f"{name},{line.strip()}")
                    name = ""
    except: pass

    # --- 4. 写入 migu.txt ---
    with open("migu.txt", "w", encoding="utf-8") as f:
        f.write("\n".join(final_output))
    print("全网硬核源提取完成")

if __name__ == "__main__":
    main()
